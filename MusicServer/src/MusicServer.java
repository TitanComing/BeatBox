import java.io.*;
import java.net.*;
import java.util.*;

/**
 * Created by peng on 17-5-1.
 * 服务器端 传送两个对象 先开服务器端,再开客户端
 */
public class MusicServer {
    ArrayList<ObjectOutputStream> clientOutputStreams;
    int ServerPort = 4242;

    public static void main(String[] args){
        new MusicServer().go();
    }

    public class ClientHandler implements Runnable{
        ObjectInputStream in;
        Socket clientSocket;

        public ClientHandler(Socket socket){
            try {
                clientSocket = socket;
                in = new ObjectInputStream(clientSocket.getInputStream());
            }catch (Exception ex){
                ex.printStackTrace();
            }
        }

        public void run(){
            Object o2 = null;
            Object o1 = null;

            try {
                while ((o1 = in.readObject()) != null){
                    o2 = in.readObject();

                    System.out.println("read two object");
                    tellEveryOne(o1,o2);
                }
            }catch (Exception ex){
                ex.printStackTrace();
            }
        }
    }


    public void tellEveryOne(Object one, Object two){
        Iterator it = clientOutputStreams.iterator(); //迭代,遍历
        while (it.hasNext()){
            try {
                ObjectOutputStream out = (ObjectOutputStream) it.next();
                out.writeObject(one);
                out.writeObject(two);
            }catch (Exception ex){
                ex.printStackTrace();
            }
        }
    }

    public void go(){
        clientOutputStreams = new ArrayList<ObjectOutputStream>();

        try{
            ServerSocket serverSock = new ServerSocket(ServerPort);
            while (true){
                Socket clientSocket = serverSock.accept();
                ObjectOutputStream out = new ObjectOutputStream(clientSocket.getOutputStream());
                clientOutputStreams.add(out);

                Thread t = new Thread(new ClientHandler(clientSocket));
                t.start();

                System.out.println("got a connection");
            }
        }catch (Exception ex){
            ex.printStackTrace();
        }
    }

}
