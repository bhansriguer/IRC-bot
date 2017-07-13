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
import java.util.StringTokenizer;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author bhans
 */
public class Main extends javax.swing.JFrame {

    private Questions mQuestions;
    private final ArrayList<Questions> mQuestionsList = new ArrayList<>();

    // The server to connect to and our details.
    String server = "irc.freenode.net";
    String nick = "thefighting";
    String login = "thefighting";

    // The channel which the bot will join.
    String channel = "#trivialand";
    String bot = "glime";

    // Connect directly to the IRC server.
    Socket socket = null;

    BufferedWriter writer = null;
    BufferedReader reader = null;

    /**
     * Creates new form Main
     */
    public Main() {
        initComponents();

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
            textArea.append(nick + ": " + message + "\n");
            writer.flush();
        } catch (IOException ex) {
            Logger.getLogger(Main.class.getName()).log(Level.SEVERE, null, ex);
            System.out.println(ex.getMessage());
        }
        textArea.setCaretPosition(textArea.getText().length());
    }

    public void localInit() {
        try {

            socket = new Socket(server, 6667);
            writer = new BufferedWriter(
                    new OutputStreamWriter(socket.getOutputStream()));
            reader = new BufferedReader(
                    new InputStreamReader(socket.getInputStream()));

            // Log on to the server.
            // Set nick
            writer.write("NICK " + nick + "\r\n");
            writer.write("USER " + login + " 8 * : Java IRC Hacks Bot\r\n");
            writer.flush();

            // Read lines from the server until it tells us we have connected.
            String line = null;
            while ((line = reader.readLine()) != null) {
                if (line.indexOf("004") >= 0) {
                    // We are now logged in.
                    break;
                } else if (line.indexOf("433") >= 0) {
                    textArea.append("Nickname is already in use.\n");
                    return;
                }
            }

            // Join the channel.
            writer.write("JOIN " + channel + "\r\n");
            writer.flush();
            // Keep reading lines from the server.
            while ((line = reader.readLine()) != null) {
                if (line.toLowerCase().startsWith("ping ")) {
                    // We must respond to PINGs to avoid being disconnected.
                    writer.write("PONG " + line.substring(5) + "\r\n");
                    writer.flush();
                } else {
                    String name = line.split("!")[0];
                    name = name.substring(1, name.length());
                    String[] message = line.split(":");
                    textArea.append(name + ": " + message[message.length - 1] + "\n");
                    textArea.setCaretPosition(textArea.getText().length());
                    // Answer trivias
                    if (name.toLowerCase().equals(bot)) {
                        String question = message[message.length - 1];
                        String hint = "";
                        if (message[message.length - 2].toLowerCase().contains("hint")) {
                            hint = message[message.length - 1];
                            String answer = confirmAnswer(hint);
                            if (!answer.equals("") && !prevAnswer.equals("")) {
                                System.out.println("Confirmed Answer: " + answer);
                                StringSelection stringSelection = new StringSelection(answer.toLowerCase());
                                Clipboard clpbrd = Toolkit.getDefaultToolkit().getSystemClipboard();
                                clpbrd.setContents(stringSelection, null);
                            } else {
                                answer = getRegexAnswer(hint);
                                System.out.println("regex hint: " + answer);
                                StringSelection stringSelection = new StringSelection(answer.toLowerCase());
                                Clipboard clpbrd = Toolkit.getDefaultToolkit().getSystemClipboard();
                                clpbrd.setContents(stringSelection, null);
                            }
                        } else if (!question.contains("ime's up!") && !question.contains("ING DING DING") && !line.contains("Total Points TO") && !question.contains("Points to")) {
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

        javax.swing.GroupLayout jPanel1Layout = new javax.swing.GroupLayout(jPanel1);
        jPanel1.setLayout(jPanel1Layout);
        jPanel1Layout.setHorizontalGroup(
            jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel1Layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(jScrollPane1)
                    .addGroup(jPanel1Layout.createSequentialGroup()
                        .addComponent(textField, javax.swing.GroupLayout.PREFERRED_SIZE, 675, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(sendButton, javax.swing.GroupLayout.DEFAULT_SIZE, 97, Short.MAX_VALUE)))
                .addContainerGap())
        );
        jPanel1Layout.setVerticalGroup(
            jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel1Layout.createSequentialGroup()
                .addContainerGap()
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
    private javax.swing.JPanel jPanel1;
    private javax.swing.JScrollPane jScrollPane1;
    private javax.swing.JButton sendButton;
    private javax.swing.JTextArea textArea;
    private javax.swing.JTextField textField;
    // End of variables declaration//GEN-END:variables
}
