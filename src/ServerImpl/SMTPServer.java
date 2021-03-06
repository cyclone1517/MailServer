package ServerImpl;

import JavaBean.Entity.Mail;
import JavaBean.Entity.User;
import JavaBean.Entity.FriendInfo;
import JavaBean.Dao.LogDao;
import JavaBean.Dao.MailDao;
import JavaBean.Dao.UserDao;
import JavaBean.Dao.FriendDao;
import JavaImpl.MailImpl;
import JavaImpl.UserImpl;
import JavaImpl.FriendImpl;
import ServerInterface.LogManage;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.sql.Timestamp;
import java.util.List;

public class SMTPServer extends Thread{

    private java.net.Socket client;
    private OutputStream ops;
    private InputStream ips;
    private BufferedReader buffread;
    private String from;
    private String to;
    private String subject;
    private String content;
    private boolean flag;

    public SMTPServer(java.net.Socket client) {
        this.client = client;
    }

    public void sendMsgToMe(String msg){
        byte[] data = msg.getBytes();
        try {
            System.out.println("[SMTPServer]data is:" + data);
            ops.write(data);
            ops.flush();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public boolean isFlag() {
        return flag;
    }

    public void setFlag(boolean flag) {
        this.flag = flag;
    }

    private boolean state = false;
    ServerSocket server;

    private String[] commands = {"helo", "auth", "mail", "rcpt", "data", "quit"};

    public void handleInput(SMTPServer server, String inStr) {

        if (!checkCommand(inStr)) {
            server.sendMsgToMe("500 Invalid command");
            return;
        }
        String com = this.getCommand(inStr);
        String arg = this.getArgument(inStr);

        if ("quit".equals(com)) {
            server.setFlag(false);
            return;
        }
    }
    private String getCommand(String inStr) {
        int bPos = inStr.indexOf(" ");
        if (bPos == -1)
            return inStr.toLowerCase();
        return inStr.substring(0, bPos).toLowerCase();
    }
    private String getArgument(String inStr) {
        int bPos = inStr.indexOf(" ");
        if (bPos == -1)
            return "";
        return inStr.substring(bPos + 1, inStr.length());
    }
    private boolean checkCommand(String inStr) {
        if ("".equals(inStr))
            return false;

        String com = getCommand(inStr);

        for (int i = 0; i < commands.length; i++)
            if (commands[i].endsWith(com))
                return true;
        return false;
    }
    private void processChat(Socket client){
		String servername = "bro.com";
        try {
            ops = client.getOutputStream();
            ips = client.getInputStream();
            buffread = new BufferedReader(new InputStreamReader(ips));
            ObjectInputStream ois = new ObjectInputStream(ips);
            if (!welcomeAndLogin()){
                client.close();
            }
            sendMsgToMe("\r\nYou have been logged in successfully!\r\n");
			sendMsgToMe("220 bro.com\n");

            Mail mail = new Mail();
            int state = 0;
            StringBuilder stringBuilder = new StringBuilder();
            String str = "";

            while (true) {
                str = buffread.readLine();
                try {
                    Mail mail1 = (Mail)ois.readObject();
                } catch (ClassNotFoundException e) {
                    e.printStackTrace();
                }
                if (str.toUpperCase().startsWith("HELO")) {
					String clientname = str.substring(str.indexOf(" ")+1, str.length());
					sendMsgToMe("250 Hello "+clientname+", Please to meet you\n");
				}
				else if(str.toUpperCase().contains("MAIL FROM:")) {
                    String sender = str.substring(str.indexOf('<')+1, str.lastIndexOf('>'));
                    mail.setFrom(sender);
                    sendMsgToMe("250 "+sender+"... sender OK\n");
                }
				else if (str.toUpperCase().contains("RCPT TO:")) {
				    str = str.substring(str.indexOf('<')+1,str.lastIndexOf('>'));
                    String[] receivers = str.split(";");
                    for (int i = 0; i < receivers.length; i++) {
                        sendMsgToMe("250 "+receivers[i]+"... receiver OK\n");
                    }

                    mail.setToList(receivers);
//                  群发实现
//                    mail.setTo(receiver);

                }
				else if (str.toUpperCase().equals("SUBJ")) {
                    sendMsgToMe("350 Enter Subject. end with \\n \n");
                    state=1;
                    continue;
                }
				else if (str.toUpperCase().equals("DATA")) {
                    sendMsgToMe("354 Enter mail, end with \".\" on a line by itself\n");
                    state=2;
                    continue;
                }
				else if (str.toUpperCase().equals("QUIT")) {
					sendMsgToMe("221 "+servername+"\n");
                    break;
                }
                /**
                 * @author: YukonChen
                 * 自定义朋友搜索指令
                 */
                else if (str.equals("FRND")){
                    FriendDao fd = new FriendImpl();
                    List<FriendInfo> friendInfoList = fd.searchFriend("1000", "200");
                    int len = (friendInfoList==null)? 0:friendInfoList.size();
                    if(len==0){
                        sendMsgToMe("Sorry, you have not added any friends\r\n");
                    }
                    for(int ind=0; ind<len; ind++){
                        FriendInfo friendInfo = friendInfoList.get(ind);
                        sendMsgToMe("#" + ind + ": " + friendInfo.getkeywordRst() + "\r\n");
                    }
                }

                if (state==1) {
                    mail.setSubject(str);
                    sendMsgToMe("Subject OK\n");
                }
                else if (state==2) {
                    if (str.equals(".")) {
                        mail.setContent(stringBuilder.toString());
                        sendMsgToMe("Content OK\n");
                    }else {
                        stringBuilder.append(str);
                    }
                }

            }
            Timestamp time = new Timestamp(new java.util.Date().getTime());
            mail.setTime(time);
            MailDao mailDao = new MailImpl();
            mailDao.storeMail(mail);
            client.close();
        } catch (IOException e) {
            e.printStackTrace();
        }

    }
    private void processChat2(Socket client) throws IOException {
        ops = client.getOutputStream();
        ips = client.getInputStream();
        buffread = new BufferedReader(new InputStreamReader(ips));
        ObjectInputStream ois = new ObjectInputStream(ips);
        Mail mail;
        while (true) {
            try {
                mail = (Mail)ois.readObject();
                client.shutdownInput();
                System.out.println("Client send mail at: "+mail.getTime());
                receiveMail(mail);
                client.close();
            } catch (ClassNotFoundException e) {
                e.printStackTrace();
            }
        }
    }
    private void receiveMail(Mail mail) {
        MailDao mailDao = new MailImpl();
        mailDao.storeMail(mail);
    }
    private boolean welcomeAndLogin(){
        try {
            sendMsgToMe("Welcome to pretended brothers' mailServer!\r\n");
            sendMsgToMe("please enter your userId: ");
            String userId = buffread.readLine();
            sendMsgToMe("\r\nplease enter your password: ");
            String password = buffread.readLine();

            UserDao userDao = new UserImpl();
            User user = userDao.login(userId, password);
            if(user==null){
                sendMsgToMe("\r\nsorry no such user exists");
                return false;
            }
            else{
                return true;
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return true;
    }

    public void run(){
        LogManage logManage = new LogManageImpl();
        logManage.addLog(LogDao.LogType.SMTP, client);
        System.out.println("Incoming client:" + client.getRemoteSocketAddress());
//        processChat(this.client);
        try {
            processChat2(this.client);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

}
