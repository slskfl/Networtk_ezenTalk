package com.example.networtk_ezentalk;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import android.content.Context;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.SystemClock;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketAddress;
import java.util.ArrayList;

// 클라이언트 만들기 (서버는 아님, 내부 IP이용)
public class MainActivity extends AppCompatActivity {

    ListView listView1;
    EditText edtSend;
    ImageView ivSendButton;
    ArrayList<ChatMessage> list;
    MyAdapter adapter;
    boolean flagConnection =true; // 서버 전원 연결 확인
    boolean isConnected=false; // 서버 연결 확인
    boolean flagRead=true;
    Handler writeHandler;
    Socket socket; // 네트워트 통신 장치
    BufferedInputStream bis; //서버로 온 데이터 읽기
    BufferedOutputStream bos; // 서버로 데이터 보내기
    SocketThread st;
    WriteThread wt;
    ReadThread rt;
    String serverIP="192.168.0.34";
    int port=7070;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        listView1 = findViewById(R.id.list1);
        edtSend = findViewById(R.id.edtSend);
        ivSendButton = findViewById(R.id.ivSendButton);
        list=new ArrayList<ChatMessage>();
        adapter=new MyAdapter(this, R.layout.chat_item, list);
        listView1.setAdapter(adapter);
        ivSendButton.setEnabled(false); //서버 연결 전까지는 false
        edtSend.setEnabled(false);
        ivSendButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if(!edtSend.getText().toString().trim().equals("")){
                    //trim() 좌우 공백
                    //메세지를 작성하지 않았을 경우에는 메세지를 보내지 않음
                    Message msg=new Message(); //메세지 클래스
                    msg.obj="리나 : "+edtSend.getText().toString(); // 메세지를 수신하는 목적지 핸들어에 보낼 임의의 객체
                    writeHandler.sendMessage(msg);
                }
            }
        });
    }

    //핸들러를 활용하여 통신 서비스
    Handler mainHandler=new Handler(){
        @Override
        public void handleMessage(@NonNull Message msg) {
            super.handleMessage(msg);
            if(msg.what==10){
                showToast("서버와 연결이 되었습니다.");
                edtSend.setEnabled(true);
                ivSendButton.setEnabled(true);
            } else if(msg.what==20){
                showToast("서버와 연결이 원활하지 않습니다.");
                edtSend.setEnabled(false);
                ivSendButton.setEnabled(false);
            } else if(msg.what==100){
                addMessage("you", (String)msg.obj);
            } else if(msg.what==200){
                addMessage("me", (String)msg.obj);
            }
        }
    };

    //메세지 내용을 추가하는 메서드
    private void addMessage(String who, String msg){
        //리스트 뷰에 메세지 추가
        ChatMessage cmsg=new ChatMessage();
        cmsg.who=who;
        cmsg.msg=msg;
        list.add(cmsg);
        adapter.notifyDataSetChanged();//새로고침 (리스트에 반영하기 위함)
        listView1.setSelection(list.size()-1); //항상 아래에 항목을 위치시킴 (항목이 많을 경우)
    }

    //onStart 메서드
    @Override
    protected void onStart() {
        super.onStart();
        //화면이 활성화됨, 초기값을 설정하기 딱 좋음
        st=new SocketThread();
        st.start();
    }

    //onStop 메서드
    @Override
    protected void onStop() {
        super.onStop();
        flagConnection=false;
        isConnected=false;
        if(socket!=null){
            flagRead=false;
            writeHandler.getLooper().quit();
            try{
                bos.close();
                bis.close();
                socket.close();
            } catch (IOException e){
                showToast("서버와 통신이 끊어졌습니다.");
            }
        }
    }

    //토스트 메세지
    void showToast(String msg){
        Toast.makeText(getApplicationContext(), msg, Toast.LENGTH_SHORT).show();
    }

    //Socket 클래스(서버와 연결)
    class SocketThread extends Thread{
        @Override
        public void run() {
            // 서버로 들어온 클라이언트만큼 소켓 만들기
            while(flagConnection){
                try {
                    if(!isConnected){
                        //서버와 연결이 성공되는 true, 보통 한번만 실행
                        socket=new Socket();
                        SocketAddress address=new InetSocketAddress(serverIP, port);
                        socket.connect(address, 10000);
                        bis=new BufferedInputStream(socket.getInputStream());
                        bos=new BufferedOutputStream(socket.getOutputStream());
                        if(rt!=null){
                            // 읽을 메세지가 있을 경우
                            flagRead=false;
                        }
                        if(wt!=null){
                            // 쓸 메세지가 있을 경우
                            writeHandler.getLooper().quit();
                        }
                        wt=new WriteThread();
                        wt.start();
                        rt=new ReadThread();
                        rt.start();
                        Message msg=new Message();
                        msg.what=10;
                        isConnected=true;
                        mainHandler.sendMessage(msg);
                    }else {
                        //서버와 연결이 실패될 경우
                        SystemClock.sleep(10000);
                    }
                } catch (Exception e){
                    showToast("서버와의 연결 시동 중.. 잠시만 기다려주세요.");
                    SystemClock.sleep(10000);
                }
            }
        }
    }

    //쓰기 스레드(서버로 보냄)
    class WriteThread extends Thread{
        //리스트 뷰에 적힌 텍스트를 실시간으로 변화시켜주는 스레드
        @Override
        public void run() {
            Looper.prepare();
            writeHandler=new Handler(){
                @Override
                public void handleMessage(@NonNull Message msg) {
                    try{
                        //서버로 메세지를 보낼 수 있음
                        bos.write(((String)msg.obj).getBytes());
                        bos.flush();
                        Message message=new Message();
                        message.what=200;
                        message.obj=msg.obj;
                        mainHandler.sendMessage(message);
                    } catch (Exception e){
                        showToast("서버와 연결이 끊어졌습니다.");
                        isConnected=false;
                        writeHandler.getLooper().quit();
                        try{
                            flagRead=false;
                        } catch (Exception e1){
                            showToast("오류가 발생했습니다.");
                        }
                    }
                }
            };
            Looper.loop();
        }
    }

    //읽기 스레드(서버로부터 받음)
    class ReadThread extends Thread{
        @Override
        public void run() {
            byte[] buffer=null;
            while(flagRead){
                buffer=new byte[1024];
                try{
                    String message=null;
                    int size=bis.read(buffer);
                    if(size>0){
                        message=new String(buffer, 0, size, "utf-8");
                        if(message!=null && !message.equals("")){
                            //메세지 데이터가 전달됨
                            Message msg=new Message();
                            msg.what=100;
                            msg.obj=message;
                            mainHandler.sendMessage(msg);
                        }
                    }else{
                        flagRead=false;
                        isConnected=false;
                    }
                } catch (Exception e){
                    showToast("서버와 연결이 끊어졌습니다.");
                    flagRead=false;
                    isConnected=false;
                }
            }
            Message msg=new Message();
            msg.what=20;
            mainHandler.sendMessage(msg);
        }
    }

    //내용 클래스
    class ChatMessage {
        String who; // 누구에겐 온 메세지인지 구별하기 위함(상대방 or 나)
        String msg; // 메세지 내용
    }

    //어댑터 클래스
    class MyAdapter extends ArrayAdapter<ChatMessage>{
        ArrayList<ChatMessage> list;
        int resID;
        Context context;
        public MyAdapter(@NonNull Context context, int resID, ArrayList<ChatMessage> list){
            super(context, resID, list);
            this.context=context;
            this.resID=resID;
            this.list=list;
        }

        @NonNull
        @Override
        public View getView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
            LayoutInflater inflater=(LayoutInflater)context.getSystemService(context.LAYOUT_INFLATER_SERVICE);
            convertView=inflater.inflate(resID, null);
            TextView msgView=convertView.findViewById(R.id.item_msg);
            RelativeLayout.LayoutParams params=(RelativeLayout.LayoutParams)msgView.getLayoutParams();
            ChatMessage msg =list.get(position);
            if(msg.who.equals("me")){
                //오른쪽(내가 보내는 메세지 모양)
                params.addRule(RelativeLayout.ALIGN_PARENT_RIGHT, RelativeLayout.TRUE);
                msgView.setTextColor(Color.WHITE);
                msgView.setBackgroundResource(R.drawable.chat_right);
            }else if(msg.who.equals("you")){
                params.addRule(RelativeLayout.ALIGN_PARENT_LEFT, RelativeLayout.TRUE);
                msgView.setTextColor(Color.WHITE);
                msgView.setBackgroundResource(R.drawable.chat_left);
            }
            msgView.setText(msg.msg);
            return convertView;
        }
    }
}
