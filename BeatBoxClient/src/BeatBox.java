import javax.sound.midi.*;
import javax.swing.*;
import javax.swing.event.*;
import java.awt.event.*;
import java.io.*;
import java.net.*;
import java.util.*;
import java.awt.*;

/**
 * Created by peng on 2017/4/15.
 */
public class BeatBox {
    JFrame theFrame;
    JPanel mainPanel;
    JList incomingList;
    JTextField userMessage;
    ArrayList<JCheckBox> checkboxList; //把checkbox储存在ArrayList中
    int nextNum;
    Vector<String> listVector = new Vector<String>();
    String userName;
    ObjectOutputStream out;
    ObjectInputStream in;
    HashMap<String, boolean[]> otherSeqsMap = new HashMap<String, boolean[]>();

    Sequencer sequencer;
    Sequence sequence;
    Sequence mySequece = null;
    Track track;

    String ServerIp = "127.0.0.1";
    int ServerPort = 4242;

    //乐器的名称，以String的array维护
    /*
    String[] instrumentNames = {"Bass Drum", "Closed Hi-Hat",
            "Open Hi-Hat", "Acoustic Snare", "Crash Cymbal", "Hand Clap",
            "High Tom", "Hi Bongo", "Maracas", "Whistle", "Low Conga",
            "Cowbell", "Vibraslap", "Low-mid Tom", "High Agogo",
            "Open Hi Conga"};
     */
    String[] instrumentNames = {"低音鼓", "关踩钹", "开踩钹", "声圈", "打击钹", "拍手",
            "高音汤姆", "小手鼓", "沙球", "哨子", "低音康茄",
            "铃铛", "镇音板", "低音汤姆", "高音打铃", "康佳"};
    //实际的乐器关键字，例如35是bass，42是Closed Hi-Hat
    int[] instruments = {35, 42, 46, 38, 49, 39, 50, 60, 70, 72, 64, 56, 58, 47, 67, 63};

    public static void main(String[] args) {
        new BeatBox().startUp();
    }

    public void startUp() {
        userName = getUserName();
        //open connection to server
        try {
            Socket sock = new Socket(ServerIp, ServerPort);
            out = new ObjectOutputStream(sock.getOutputStream());
            in = new ObjectInputStream(sock.getInputStream());
            Thread remote = new Thread(new RemoteReader());
            remote.start();
        } catch (Exception ex) {
            System.out.println("无法连接服务器,只能自己玩会了......");
        }
        setUpMidi();
        buildGUI();
    }

    public void buildGUI() {
        theFrame = new JFrame("网络电子乐发生器" + "@" + userName);
        theFrame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        BorderLayout layout = new BorderLayout();
        JPanel background = new JPanel(layout);
        //设定面板上摆设组件时的空白边缘
        background.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        //右侧控制按键，注册监听事件
        checkboxList = new ArrayList<JCheckBox>();
        Box buttonBox = new Box(BoxLayout.Y_AXIS);

        JButton start = new JButton("开始");
        start.addActionListener(new MyStartListener());
        buttonBox.add(start);

        JButton stop = new JButton("停止");
        stop.addActionListener(new MyStopListener());
        buttonBox.add(stop);

        JButton upTempo = new JButton("升调");
        upTempo.addActionListener(new MyUpTempoListener());
        buttonBox.add(upTempo);

        JButton downTempo = new JButton("降调");
        downTempo.addActionListener(new MyDownTempoListener());
        buttonBox.add(downTempo);

        JButton reset = new JButton("重置");
        reset.addActionListener(new MyResetListener());
        buttonBox.add(reset);

        JButton randomButtom = new JButton("随机生成");
        randomButtom.addActionListener(new MyRandomListener());
        buttonBox.add(randomButtom);

        JButton sendIt = new JButton("发送");
        sendIt.addActionListener(new MySendListener());
        buttonBox.add(sendIt);

        //输入发送文本信息
        userMessage = new JTextField();
        buttonBox.add(userMessage);

        //显示收到的信息的组件
        incomingList = new JList();
        incomingList.addListSelectionListener(new MyListSelectionListener());
        incomingList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        JScrollPane theList = new JScrollPane(incomingList);
        buttonBox.add(theList);
        incomingList.setListData(listVector);


        //设置菜单
        JMenuBar menuBar = new JMenuBar();
        JMenu fileMenu = new JMenu("文件");
        JMenuItem saveMenuItem = new JMenuItem("保存");
        JMenuItem loadMenuItem = new JMenuItem("加载");
        saveMenuItem.addActionListener(new saveMenuListener());
        loadMenuItem.addActionListener(new loadMenuListener());
        fileMenu.add(saveMenuItem);
        fileMenu.add(loadMenuItem);
        menuBar.add(fileMenu);
        theFrame.setJMenuBar(menuBar);


        //左侧乐器label
        Box nameBox = new Box(BoxLayout.Y_AXIS);
        for (int i = 0; i < 16; i++) {
            nameBox.add(new Label(instrumentNames[i]));
        }

        //添加到面板上
        background.add(BorderLayout.EAST, buttonBox);
        background.add(BorderLayout.WEST, nameBox);


        //添加到框架上
        theFrame.getContentPane().add(background);

        //设置中间部分网格,两个panel叠加
        GridLayout grid = new GridLayout(16, 16);
        grid.setVgap(1);
        grid.setHgap(2);
        mainPanel = new JPanel(grid);
        background.add(BorderLayout.CENTER, mainPanel);

        //创建checkbox组，设定成未勾选。并加入到ArrayList和面板上
        for (int i = 0; i < 256; i++) {
            JCheckBox c = new JCheckBox();
            c.setSelected(false);
            checkboxList.add(c);
            mainPanel.add(c);
        }

        //框架参数
        theFrame.setBounds(50, 50, 300, 300);
        theFrame.pack();
        theFrame.setVisible(true);

        //设置MIDI接口
        setUpMidi();
    }

    //一般的MIDI设置程序代码
    public void setUpMidi() {
        try {
            sequencer = MidiSystem.getSequencer();
            sequencer.open();
            sequence = new Sequence(Sequence.PPQ, 4);
            track = sequence.createTrack();
            sequencer.setTempoInBPM(120);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void buildTrackAndStart() {
        ArrayList<Integer> trackList = null;

        //保证track是最新的
        sequence.deleteTrack(track);
        track = sequence.createTrack();

        //对每个乐器都执行一次
        for (int i = 0; i < 16; i++) {
            trackList = new ArrayList<Integer>();

            //对每一拍（16个）执行一次,添加的一个乐器
            for (int j = 0; j < 16; j++) {
                JCheckBox jc = (JCheckBox) checkboxList.get(j + (16 * i));//绝对位置，相当从整体上判断
                if (jc.isSelected()) {
                    //设定乐器关键子
                    int key = instruments[i];
                    trackList.add(key);
                } else {
                    trackList.add(null);
                }
            }

            //创建此乐器的事件并加到track上
            makeTracks(trackList);
            track.add(makeEvent(176, 1, 127, 0, 16));
        }

        //确保第16拍有时间，否则beatbox不会重复播放
        track.add(makeEvent(192, 9, 1, 0, 15));
        try {
            sequencer.setSequence(sequence);
            sequencer.setLoopCount(sequencer.LOOP_CONTINUOUSLY); //指定无穷的重复次数
            //开始播放
            sequencer.start();
            sequencer.setTempoInBPM(120);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }//buildTrackAndStart方法

    //内部类 监听按钮
    //启动
    public class MyStartListener implements ActionListener {
        @Override
        public void actionPerformed(ActionEvent a) {
            buildTrackAndStart();
        }
    }

    public class MyStopListener implements ActionListener {
        @Override
        public void actionPerformed(ActionEvent a) {
            sequencer.stop();
        }
    }

    //节奏因子，预设为1.0，每次调整0.03
    public class MyUpTempoListener implements ActionListener {
        @Override
        public void actionPerformed(ActionEvent a) {
            // TODO Auto-generated method stub
            float tempoFactor = sequencer.getTempoFactor();
            sequencer.setTempoFactor((float) (tempoFactor * 1.03));
        }
    }

    public class MyDownTempoListener implements ActionListener {
        @Override
        public void actionPerformed(ActionEvent a) {
            // TODO Auto-generated method stub
            float tempoFactor = sequencer.getTempoFactor();
            sequencer.setTempoFactor((float) (tempoFactor * 0.97));
        }
    }

    //重置节奏控制面板
    public class MyResetListener implements ActionListener {
        @Override
        public void actionPerformed(ActionEvent a) {
            boolean[] checkboxState = new boolean[256];
            changeSequence(checkboxState);
            sequencer.stop();
            buildTrackAndStart();
        }
    }

    public class MyRandomListener implements ActionListener {
        @Override
        public void actionPerformed(ActionEvent a) {
            boolean[] checkboxState = new boolean[256];
            //最多选中96个音符
            int count = (int) (Math.random() * 96);
            for (int i = 0; i < count; i++) {
                checkboxState[(int) (Math.random() * 256)] = true;
            }
            changeSequence(checkboxState);
        }
    }

    public class MySendListener implements ActionListener {
        @Override
        public void actionPerformed(ActionEvent a) {
            boolean[] checkboxState = getCheckboxState();
            try {
                out.writeObject(userName + nextNum++ + ":" + userMessage.getText());
                out.writeObject(checkboxState);
            } catch (Exception ex) {
                System.out.println("抱歉,无法发送至服务器");
            }
            userMessage.setText("");
        }
    }

    public class MyListSelectionListener implements ListSelectionListener {
        @Override
        public void valueChanged(ListSelectionEvent le) {
            if (!le.getValueIsAdjusting()) {
                String selected = (String) incomingList.getSelectedValue();
                if (selected != null) {
                    boolean[] selectedState = (boolean[]) otherSeqsMap.get(selected);
                    changeSequence(selectedState);
                    sequencer.stop();
                    buildTrackAndStart();
                }
            }
        }
    }

    public class RemoteReader implements Runnable {
        boolean[] checkboxState = null;
        String nameToShow = null;
        Object obj = null;

        @Override
        public void run() {
            try {
                while ((obj = in.readObject()) != null) {
                    System.out.println("从服务器获得一个数据");
                    System.out.println(obj.getClass());
                    String nameToShow = (String) obj;
                    checkboxState = (boolean[]) in.readObject();
                    otherSeqsMap.put(nameToShow, checkboxState);
                    listVector.add(nameToShow);
                    incomingList.setListData(listVector);
                }
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        }
    }

    public void changeSequence(boolean[] checkboxState) {
        for (int i = 0; i < 256; i++) {
            JCheckBox check = (JCheckBox) checkboxList.get(i);
            if (checkboxState[i]) {
                check.setSelected(true);
            } else {
                check.setSelected(false);
            }
        }
    }

    public boolean[] getCheckboxState() {
        boolean[] checkboxState = new boolean[256]; //获取状态
        for (int i = 0; i < 256; i++) {
            JCheckBox check = (JCheckBox) checkboxList.get(i);
            if (check.isSelected()) {
                checkboxState[i] = true;
            }
        }
        return checkboxState;
    }

    public class saveMenuListener implements ActionListener {
        public void actionPerformed(ActionEvent a) {
            JFileChooser fileSave = new JFileChooser(".");
            //文件过滤器
            //FileFilter filter = new FileFilter(".ser", new String[]{"ser"});
            //fileSave.setFileFilter((javax.swing.filechooser.FileFilter) filter);
            int result = fileSave.showSaveDialog(theFrame);
            // 当用户没有选择文件,而是自己键入文件名称时,系统会自动以此文件名建立新文件.
            if (result == JFileChooser.APPROVE_OPTION) {
                File file = fileSave.getSelectedFile();
                String filePath = file.getAbsolutePath();
                //判定文件是否存在
                if (file.exists()) {
                    int copy = JOptionPane.showConfirmDialog(null,
                            "是否要覆盖当前文件？", "保存", JOptionPane.YES_NO_OPTION,
                            JOptionPane.QUESTION_MESSAGE);
                    if (copy == JOptionPane.YES_OPTION) {
                        saveFile(filePath);
                    }
                } else {
                    try {
                        file.createNewFile();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                    saveFile(filePath);
                }
            }
        }
    }

    private void saveFile(String filePath) {
        boolean[] checkboxState = getCheckboxState();
        try {
            FileOutputStream fileOut = new FileOutputStream(new File(filePath));//存储成了序列化的一种文件
            ObjectOutputStream os = new ObjectOutputStream(fileOut);
            os.writeObject(checkboxState);
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    //打开文件存储面板
    public class loadMenuListener implements ActionListener {
        public void actionPerformed(ActionEvent a) {
            JFileChooser fileOpen = new JFileChooser(".");
            //文件过滤器
            // FileFilter filter = new ExtensionFileFilter(".ser", new String[]{"ser"});
            // fileOpen.setFileFilter(filter);
            fileOpen.showOpenDialog(theFrame);
            File file = fileOpen.getSelectedFile();
            String filePath = file.getAbsolutePath();
            loadFile(filePath);
        }
    }

    private void loadFile(String filePath) {
        //读取数据
        boolean[] checkboxState = null;
        try {
            FileInputStream fileIn = new FileInputStream(new File(filePath));
            ObjectInputStream is = new ObjectInputStream(fileIn);
            checkboxState = (boolean[]) is.readObject();
        } catch (Exception ex) {
            ex.printStackTrace();
        }

        //根据数据还原状态
        changeSequence(checkboxState);
        sequencer.stop();
        buildTrackAndStart();
    }

    //创建某项乐器的所有事件
    public void makeTracks(ArrayList<Integer> list) {
        Iterator it = list.iterator();
        for (int i = 0; i < 16; i++) {
            Integer num = (Integer) it.next();
            if (num != null) {
                int numKey = num.intValue();
                //读出，对于选中的创建NOTE ON 和 NOTE OFF事件，加入到track上
                track.add(makeEvent(144, 9, numKey, 100, i));
                track.add(makeEvent(128, 9, numKey, 100, i + 1));
            }
        }
    }

    public MidiEvent makeEvent(int comd, int chan, int one, int two, int tick) {
        MidiEvent event = null;
        try {
            ShortMessage a = new ShortMessage();
            a.setMessage(comd, chan, one, two);
            event = new MidiEvent(a, tick);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return event;
    }

    public String getUserName() {
        String inputName = JOptionPane.showInputDialog("请输入您的用户名");
        if (inputName == null) {
            inputName = "默认用户";
        }
        return inputName;
    }
}

