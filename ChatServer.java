import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketAddress;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.concurrent.locks.ReentrantLock;

public class ChatServer {
	private ArrayList<Socket> socketList = new ArrayList<Socket>();
	private ReentrantLock lock = new ReentrantLock();
	
	private ServerSocket serverSocket;
	
	public ChatServer(int port)
	{
		try {
			serverSocket = new ServerSocket();
			serverSocket.setReuseAddress(true);
			serverSocket.bind(new InetSocketAddress(port));
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	public void addSocket(Socket socket) {
		lock.lock();
        socketList.add(socket);
        lock.unlock();
	}
	
	public void removeSocket(Socket socket) {
		lock.lock();
		socketList.remove(socket);
		lock.unlock();
		try {
        	socket.close();
        }catch(IOException e){
        	e.printStackTrace();
        }
    }
	
	public void chat(Socket socket, byte[] msg) {
		for(Socket s : socketList) {
			if (s != socket) {
				try {
					if(s.isConnected()) {
						OutputStream outputStream = s.getOutputStream();
						outputStream.write(msg);
						outputStream.flush();
					}
				} catch(IOException e) {
					
				}
			}
		}
    }
	
	public void mainLoop()
	{
		System.out.println("Start Server");
		while(true) {
			try {
				Socket client = serverSocket.accept();
				
				this.addSocket(client);
				SocketThread t = new SocketThread(client);
				t.start();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}
	
	public class SocketThread extends Thread{
		Socket socket;
		InputStream is;
		
		public SocketThread(Socket socket) {
			this.socket = socket;		
		}

		@Override
		public void run() {
			System.out.println("Thread start!");
			while(true) {
				
				try {
				
					byte[] buf = new byte[1518];
					is = socket.getInputStream();
					if(is.read(buf) < 0) {
						removeSocket(socket);
						System.out.println("Thread Exit. Remind Socket Count :" + socketList.size());
						return;
					} else if(buf.length > 0) {
						chat(socket, buf);
						System.out.println(new String(buf));
					}
					
				}catch(IOException e){
					e.printStackTrace();
					removeSocket(socket);
					System.out.println("Thread Exit. Remind Socket Count :" + socketList.size());
					return;
				}
			}
		}
	}
}
