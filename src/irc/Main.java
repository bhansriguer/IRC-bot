/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package irc;

import java.awt.Toolkit;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.StringSelection;
import java.awt.event.KeyEvent;
import java.io.*;
import java.net.*;
import java.util.ArrayList;
import java.util.Random;
import java.util.StringTokenizer;
import java.util.Timer;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author bhans
 */
public class Main extends javax.swing.JFrame {

    private Questions mQuestions;
    private final ArrayList<Questions> mQuestionsList = new ArrayList<>();
    private Random rand = new Random();

    // The server to connect to and our details.
    private String server = "";
    private String nick = "";
    private String login = "";
    private String password = "";

    // The channel which the bot will join.
    private String channel = "";
    private String bot = "";

    // Connect directly to the IRC server.
    private Socket socket = null;

    private BufferedWriter writer = null;
    private BufferedReader reader = null;

    // Time is in ms
    private int randMinTime = 3500;
    private int randMaxTime = 7000;

    // Set bot on
    private boolean isBotEnabled = false;

    /**
     * Creates new form Main
     */
    public Main() {
        initComponents();
        tfMinTime.setText(String.valueOf(randMinTime));
        tfMaxTime.setText(String.valueOf(randMaxTime));
        Runnable r = () -> {
            initCheats();
            localInit();
        };
        new Thread(r).start();
    }

    public void initCheats() {
        String[] files = {"b01.txt", "b02.txt", "b03.txt", "b04.txt", "b05.txt", "b06.txt", "b07.txt", "b08.txt", "b09.txt", "b10.txt", "b11.txt", "b12.txt", "b13.txt", "b14.txt", "b15.txt"};
        String resourcesPath = "/res/";
        String answer = "";
        for (String f : files) {
            InputStream stream = Main.class.getResourceAsStream(resourcesPath + f);
            try (BufferedReader br = new BufferedReader(new InputStreamReader(stream))) {
                String line;
                while ((line = br.readLine()) != null) {
                    StringTokenizer st = new StringTokenizer(line, "*");
                    while (st.hasMoreTokens()) {
                        mQuestions = new Questions();
                        mQuestions.setQuestion(st.nextToken());
                        answer = st.nextToken();
                        if (st.countTokens() > 0) {
                            while (st.hasMoreTokens()) {
                                answer += "|" + st.nextToken();
                            }
                        }
                        mQuestions.setAnswer(answer);
                        mQuestionsList.add(mQuestions);
                        break;
                    }
                }
            } catch (IOException ex) {
                Logger.getLogger(Main.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
        System.out.println("Done parsing data: " + mQuestionsList.size());
    }

    private String prevAnswer = "";

    private String getAnswer(String question) {
        String answer = "";
        for (Questions q : mQuestionsList) {
            if (q.getQuestion().toLowerCase().contains(question.toLowerCase())) {
                answer = q.getAnswer();
                break;
            }
        }
        return answer;
    }

    private String confirmAnswer(String hint) {
        String finalAnswer = "";
        hint = hint.trim();
        for (Questions q : mQuestionsList) {
            if (q.getAnswer().contains(prevAnswer)) {
                StringTokenizer st = new StringTokenizer(q.getAnswer(), "|");
                while (st.hasMoreTokens()) {
                    String ans = st.nextToken();
                    if (ans.length() == hint.length()) {
                        finalAnswer = ans;
                        break;
                    }
                }
            }
        }
        return finalAnswer;
    }

    private String getRegexAnswer(String regex) {
        regex = regex.trim();
        String answer = "";
        String formRegex = regex.replaceAll("\\*", "[a-zA-Z]");
        for (Questions q : mQuestionsList) {
            if (q.getAnswer().replace("|", "").matches(".*" + formRegex + ".*")) {
                StringTokenizer st = new StringTokenizer(q.getAnswer(), "|");
                while (st.hasMoreTokens()) {
                    String ans = st.nextToken();
                    if (ans.length() == regex.length()) {
                        answer = ans;
                        break;
                    }
                }
            }
        }
        return answer;
    }

    public void sendMessage(String message) {
        boolean isCommand = false;
        String chanMsgAppend = "PRIVMSG " + channel + " :";
        try {
            if (message.startsWith("/")) {
                isCommand = true;
            }
            writer.write(isCommand ? "" : chanMsgAppend + message + "\r\n");
            writeToTextArea(nick + ": " + message + "\n");
            writer.flush();
        } catch (IOException ex) {
            Logger.getLogger(Main.class.getName()).log(Level.SEVERE, null, ex);
            System.out.println(ex.getMessage());
        }
    }

    private void writeToTextArea(String message) {
        textArea.append(message);
        textArea.setCaretPosition(textArea.getText().length());
        if (textArea.getLineCount() > 1000) {
            textArea.setText("");
        }
    }

    private Timer answerTimer;

    public void localInit() {
        try {
            // If you want to use proxies, please fill in this and uncomment
//            System.setProperty("java.net.useSystemProxies", "true");
//            System.setProperty("socksProxyHost", "192.168.227.100");
//            System.setProperty("socksProxyPort", "9666");
//            System.setProperty("socksProxyVersion", "5");

            socket = new Socket(server, 6667);
            writer = new BufferedWriter(
                    new OutputStreamWriter(socket.getOutputStream()));
            reader = new BufferedReader(
                    new InputStreamReader(socket.getInputStream()));

            // Log on to the server.
            // Set nick
            writer.write("NICK " + nick + "\r\n");
            writer.write("USER " + login + " 8 * : " + nick + "\r\n");
            writer.flush();

            // Read lines from the server until it tells us we have connected.
            String line = null;
            while ((line = reader.readLine()) != null) {
                if (line.indexOf("004") >= 0) {
                    // We are now logged in.
                    break;
                } else if (line.indexOf("433") >= 0) {
                    writeToTextArea("Nickname is already in use.\n");
                    return;
                }
            }

            if (!password.equals("")) {
                writer.write("NICKSERV identify bhans12\r\n");
                writer.flush();
                while ((line = reader.readLine()) != null) {
                    writeToTextArea(line + "\n");
                    if (line.contains("is now your hidden host")) {
                        break;
                    } else if (line.contains("not a registered nickname")) {
                        writeToTextArea("not a registered nick! \n");
                        break;
                    }
                }
            }

            // Join the channel.
            writer.write("JOIN " + channel + "\r\n");
            writer.flush();
            // Keep reading lines from the server.
            int hints = 0;
            while ((line = reader.readLine()) != null) {
                if (line.toLowerCase().startsWith("ping ")) {
                    // We must respond to PINGs to avoid being disconnected.
                    System.out.println("Pinged!: " + line);
                    writer.write("PONG " + line.substring(5) + "\r\n");
                    writer.flush();
                } else {
                    String name = line.split("!")[0];
                    name = name.substring(1, name.length());
                    String[] message = line.split(":");
                    writeToTextArea(name + ": " + message[message.length - 1] + "\n");
                    // Answer trivias
                    if (name.toLowerCase().equals(bot.toLowerCase())) {
                        String question = message[message.length - 1];
                        String hint = "";
                        if (message[message.length - 2].toLowerCase().contains("hint")) {
                            hint = message[message.length - 1];
                            final String answer = confirmAnswer(hint);
                            if (!answer.equals("") && !prevAnswer.equals("")) {
                                System.out.println("Confirmed Answer: " + answer);
                                StringSelection stringSelection = new StringSelection(answer.toLowerCase());
                                Clipboard clpbrd = Toolkit.getDefaultToolkit().getSystemClipboard();
                                clpbrd.setContents(stringSelection, null);
                                if (isBotEnabled) {
                                    int random = rand.nextInt(randMaxTime - randMinTime) + randMinTime;
                                    answerTimer.schedule(
                                            new java.util.TimerTask() {
                                        @Override
                                        public void run() {
                                            sendMessage(answer.toLowerCase());
                                        }
                                    }, random
                                    );
                                }
                                writeToTextArea("IN YOUR CLIPBOARD: " + answer + "\n");
                            } else {
                                hints++;
                                String regexAns = getRegexAnswer(hint);
                                System.out.println("regex hint: " + regexAns);
                                StringSelection stringSelection = new StringSelection(answer.toLowerCase());
                                Clipboard clpbrd = Toolkit.getDefaultToolkit().getSystemClipboard();
                                clpbrd.setContents(stringSelection, null);
                                if (isBotEnabled) {
                                    if (hints == 3 && !regexAns.equals("")) {
                                        int random = rand.nextInt(randMaxTime - randMinTime) + randMinTime;
                                        answerTimer.schedule(
                                                new java.util.TimerTask() {
                                            @Override
                                            public void run() {
                                                sendMessage(regexAns.toLowerCase());
                                            }
                                        }, random
                                        );
                                    }
                                }
                                writeToTextArea("IN YOUR CLIPBOARD: " + regexAns + "\n");
                            }
                        } else if (!question.contains("ime's up!") && !question.toLowerCase().contains("ing ding ding") && !line.contains("Total Points TO") && !question.contains("Points to")) {
                            answerTimer = new Timer();
                            StringTokenizer st = new StringTokenizer(question, " ");
                            question = "";
                            while (st.hasMoreTokens()) {
                                String str = st.nextToken();
                                if (!str.contains("----") && !str.contains("_____")) {
                                    question += str + " ";
                                } else {
                                    break;
                                }
                            }
                            question = question.trim();
                            question = question.replace("?", "");
                            String answer = getAnswer(question);
                            prevAnswer = answer;
                            System.out.println("Question: " + question);
                            System.out.println("Answer: " + answer);
                            hints = 0;
                        } else if (question.contains("ime's up!") || question.toLowerCase().contains("ing ding ding") || line.contains("Total Points TO") || question.contains("Points to")) {
                            if (answerTimer != null) {
                                answerTimer.cancel();
                                answerTimer.purge();
                                writeToTextArea("System: Auto-answer cancelled!\n");
                            }
                        }
                    }
                }
            }
        } catch (IOException ex) {
            Logger.getLogger(Main.class.getName()).log(Level.SEVERE, null, ex);
            System.out.println(ex.getMessage());
        }
    }

    /**
     * @param server the server to set
     */
    public void setServer(String server) {
        this.server = server;
    }

    /**
     * @param nick the nick to set
     */
    public void setNick(String nick) {
        this.nick = nick;
        this.login = nick;
    }

    /**
     * @param channel the channel to set
     */
    public void setChannel(String channel) {
        this.channel = channel;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    /**
     * @param bot the bot to set
     */
    public void setBot(String bot) {
        this.bot = bot;
    }

    private void setRandMinTime(int minTime) {
        this.randMinTime = minTime;
    }

    private void setRandMaxTime(int maxTime) {
        this.randMaxTime = maxTime;
    }

    /**
     * This method is called from within the constructor to initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is always
     * regenerated by the Form Editor.
     */
    @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        jPanel1 = new javax.swing.JPanel();
        jScrollPane1 = new javax.swing.JScrollPane();
        textArea = new javax.swing.JTextArea();
        textField = new javax.swing.JTextField();
        sendButton = new javax.swing.JButton();
        jLabel1 = new javax.swing.JLabel();
        tfMinTime = new javax.swing.JTextField();
        tfMaxTime = new javax.swing.JTextField();
        jLabel2 = new javax.swing.JLabel();
        btnSetTime = new javax.swing.JButton();
        btnIsBotEnabled = new javax.swing.JButton();

        setDefaultCloseOperation(javax.swing.WindowConstants.EXIT_ON_CLOSE);

        textArea.setColumns(20);
        textArea.setRows(5);
        jScrollPane1.setViewportView(textArea);

        textField.addKeyListener(new java.awt.event.KeyAdapter() {
            public void keyPressed(java.awt.event.KeyEvent evt) {
                textFieldKeyPressed(evt);
            }
        });

        sendButton.setText("Send");

        jLabel1.setText("Time to send between seconds:");

        jLabel2.setText("TO");

        btnSetTime.setText("Set Time");
        btnSetTime.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                btnSetTimeActionPerformed(evt);
            }
        });

        btnIsBotEnabled.setText("Enable Bot");
        btnIsBotEnabled.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                btnIsBotEnabledActionPerformed(evt);
            }
        });

        javax.swing.GroupLayout jPanel1Layout = new javax.swing.GroupLayout(jPanel1);
        jPanel1.setLayout(jPanel1Layout);
        jPanel1Layout.setHorizontalGroup(
            jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel1Layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(jPanel1Layout.createSequentialGroup()
                        .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(jScrollPane1)
                            .addGroup(jPanel1Layout.createSequentialGroup()
                                .addComponent(textField, javax.swing.GroupLayout.PREFERRED_SIZE, 675, javax.swing.GroupLayout.PREFERRED_SIZE)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addComponent(sendButton, javax.swing.GroupLayout.DEFAULT_SIZE, 97, Short.MAX_VALUE)))
                        .addContainerGap())
                    .addGroup(jPanel1Layout.createSequentialGroup()
                        .addComponent(jLabel1)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(tfMinTime, javax.swing.GroupLayout.PREFERRED_SIZE, 69, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(jLabel2)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(tfMaxTime, javax.swing.GroupLayout.PREFERRED_SIZE, 69, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(btnSetTime)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                        .addComponent(btnIsBotEnabled))))
        );
        jPanel1Layout.setVerticalGroup(
            jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel1Layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(jLabel1)
                    .addComponent(tfMinTime, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(tfMaxTime, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(jLabel2)
                    .addComponent(btnSetTime)
                    .addComponent(btnIsBotEnabled))
                .addGap(3, 3, 3)
                .addComponent(jScrollPane1, javax.swing.GroupLayout.PREFERRED_SIZE, 438, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING)
                    .addComponent(sendButton)
                    .addGroup(jPanel1Layout.createSequentialGroup()
                        .addComponent(textField, javax.swing.GroupLayout.PREFERRED_SIZE, 26, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addGap(3, 3, 3)))
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(getContentPane());
        getContentPane().setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addContainerGap()
                .addComponent(jPanel1, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addContainerGap())
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addContainerGap()
                .addComponent(jPanel1, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );

        pack();
    }// </editor-fold>//GEN-END:initComponents

    private void textFieldKeyPressed(java.awt.event.KeyEvent evt) {//GEN-FIRST:event_textFieldKeyPressed
        // TODO add your handling code here:
        if (evt.getKeyCode() == KeyEvent.VK_ENTER) {
            sendMessage(textField.getText());
            textField.setText("");
        }
    }//GEN-LAST:event_textFieldKeyPressed

    private void btnSetTimeActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btnSetTimeActionPerformed
        // TODO add your handling code here:
        setRandMinTime(Integer.parseInt(tfMinTime.getText()));
        setRandMaxTime(Integer.parseInt(tfMaxTime.getText()));
    }//GEN-LAST:event_btnSetTimeActionPerformed

    private void btnIsBotEnabledActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btnIsBotEnabledActionPerformed
        // TODO add your handling code here:
        if (isBotEnabled) {
            isBotEnabled = false;
            btnIsBotEnabled.setText("Enable Bot");
        } else {
            isBotEnabled = true;
            btnIsBotEnabled.setText("Disable Bot");
        }
    }//GEN-LAST:event_btnIsBotEnabledActionPerformed

    /**
     * @param args the command line arguments
     */
    public static void main(String args[]) {
        /* Set the Nimbus look and feel */
        //<editor-fold defaultstate="collapsed" desc=" Look and feel setting code (optional) ">
        /* If Nimbus (introduced in Java SE 6) is not available, stay with the default look and feel.
         * For details see http://download.oracle.com/javase/tutorial/uiswing/lookandfeel/plaf.html 
         */
        try {
            for (javax.swing.UIManager.LookAndFeelInfo info : javax.swing.UIManager.getInstalledLookAndFeels()) {
                if ("Nimbus".equals(info.getName())) {
                    javax.swing.UIManager.setLookAndFeel(info.getClassName());
                    break;
                }
            }
        } catch (ClassNotFoundException ex) {
            java.util.logging.Logger.getLogger(Main.class.getName()).log(java.util.logging.Level.SEVERE, null, ex);
        } catch (InstantiationException ex) {
            java.util.logging.Logger.getLogger(Main.class.getName()).log(java.util.logging.Level.SEVERE, null, ex);
        } catch (IllegalAccessException ex) {
            java.util.logging.Logger.getLogger(Main.class.getName()).log(java.util.logging.Level.SEVERE, null, ex);
        } catch (javax.swing.UnsupportedLookAndFeelException ex) {
            java.util.logging.Logger.getLogger(Main.class.getName()).log(java.util.logging.Level.SEVERE, null, ex);
        }
        //</editor-fold>

        /* Create and display the form */
        java.awt.EventQueue.invokeLater(new Runnable() {
            public void run() {
                new Main().setVisible(true);
            }
        });
    }

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JButton btnIsBotEnabled;
    private javax.swing.JButton btnSetTime;
    private javax.swing.JLabel jLabel1;
    private javax.swing.JLabel jLabel2;
    private javax.swing.JPanel jPanel1;
    private javax.swing.JScrollPane jScrollPane1;
    private javax.swing.JButton sendButton;
    private javax.swing.JTextArea textArea;
    private javax.swing.JTextField textField;
    private javax.swing.JTextField tfMaxTime;
    private javax.swing.JTextField tfMinTime;
    // End of variables declaration//GEN-END:variables

}
