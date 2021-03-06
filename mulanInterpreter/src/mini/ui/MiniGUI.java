/*
 * Created by Chief on Thu Nov 15 10:05:17 CST 2018
 */

package mini.ui;

import mini.component.*;
import mini.entity.Node;
import mini.entity.Token;
import net.miginfocom.swing.MigLayout;
import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyledDocument;
import java.awt.*;
import java.awt.datatransfer.DataFlavor;
import java.awt.dnd.DnDConstants;
import java.awt.dnd.DropTarget;
import java.awt.dnd.DropTargetAdapter;
import java.awt.dnd.DropTargetDropEvent;
import java.awt.event.*;
import java.io.*;
import java.util.ArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author Chief
 */

public class MiniGUI extends JFrame {

    private File inoFile;
    private File midiFile;
    private File tempMidiFile;
    private File file;

    private boolean hasSaved = false;
    private boolean hasChanged = false;
    private boolean ctrlPressed = false;
    private boolean sPressed = false;
    private boolean isLoadedMidiFile = false;

    private SimpleAttributeSet attributeSet;
    private SimpleAttributeSet statementAttributeSet;
    private SimpleAttributeSet durationAttributeSet;
    private SimpleAttributeSet normalAttributeSet;
    private SimpleAttributeSet commentAttributeSet;
    private SimpleAttributeSet errorAttributeSet;

    private StyledDocument inputStyledDocument;

    private Pattern statementPattern;
    private Pattern keywordPattern;
    private Pattern parenPattern;

    private LexicalAnalysis lexicalAnalysis;
    private SyntacticAnalysis syntacticAnalysis;
    private SemanticAnalysisArduino semanticAnalysisArduino;
    private SemanticAnalysisMidi semanticAnalysisMidi;
    private ArduinoCmd arduinoCmd;
    private MidiPlayer midiPlayer;

    private String cmdOutput;

    public MiniGUI() {
        initComponents();
        setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);

        //样式
        attributeSet = new SimpleAttributeSet();
        statementAttributeSet = new SimpleAttributeSet();
        durationAttributeSet = new SimpleAttributeSet();
        normalAttributeSet = new SimpleAttributeSet();
        commentAttributeSet = new SimpleAttributeSet();
        errorAttributeSet = new SimpleAttributeSet();
        StyleConstants.setForeground(attributeSet, new Color(92, 101, 192));
        StyleConstants.setBold(attributeSet, true);
        StyleConstants.setForeground(statementAttributeSet, new Color(30, 80, 180));
        StyleConstants.setBold(statementAttributeSet, true);
        StyleConstants.setForeground(durationAttributeSet, new Color(111, 150, 255));
        StyleConstants.setForeground(commentAttributeSet, new Color(128, 128, 128));
        StyleConstants.setForeground(errorAttributeSet, new Color(238, 0, 1));
        inputStyledDocument = inputTextPane.getStyledDocument();
        statementPattern = Pattern.compile("\\bparagraph\\b|\\bend\\b|\\bplay");
        keywordPattern = Pattern.compile("\\bspeed=|\\binstrument=|\\bvolume=|\\b1=");
        parenPattern = Pattern.compile("<(\\s*\\{?\\s*(1|2|4|8|g|w|\\*)+\\s*\\}?\\s*)+>");

        //关闭窗口提示
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                //删除临时ino文件
                if (showSaveComfirm("Exist unsaved content, save before exit?")) {
                    File tempFile = tempMidiFile;

                    if (tempFile != null && tempFile.exists())
                        tempFile.delete();

                    tempFile = new File("C:\\Users\\Chief\\Documents\\Arduino\\temp.ino");

                    if (tempFile.exists())
                        tempFile.delete();

                    System.exit(0);
                }
            }
        });

        //着色与补全的监听
        inputTextPane.addKeyListener(new KeyAdapter() {
            @Override
            public void keyReleased(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_UP ||
                        e.getKeyCode() == KeyEvent.VK_DOWN ||
                        e.getKeyCode() == KeyEvent.VK_LEFT ||
                        e.getKeyCode() == KeyEvent.VK_RIGHT ||
                        e.getKeyCode() == KeyEvent.VK_BACK_SPACE ||
                        e.getKeyCode() == KeyEvent.VK_SHIFT ||
                        e.getKeyCode() == KeyEvent.VK_ALT)
                    return;

                if (e.getKeyCode() == KeyEvent.VK_CONTROL) {
                    ctrlPressed = false;
                    return;
                }

                if (e.getKeyCode() == KeyEvent.VK_S) {
                    sPressed = false;
                    return;
                }

                autoComplete();
                refreshColor();
            }

            @Override
            public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_BACK_SPACE) {
                    autoRemove();
                    refreshColor();
                }

                if (e.getKeyCode() == KeyEvent.VK_CONTROL)
                    ctrlPressed = true;

                if (e.getKeyCode() == KeyEvent.VK_S)
                    sPressed = true;

                if (ctrlPressed && sPressed) {
                    sPressed = false;
                    ctrlPressed = false;
                    saveMenuItemActionPerformed(null);
                }
            }
        });

        //是否有改动的监听
        inputTextPane.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                contentChanged();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                contentChanged();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                contentChanged();
            }
        });

        //组件实例化
        lexicalAnalysis = new LexicalAnalysis();
        syntacticAnalysis = new SyntacticAnalysis();
        semanticAnalysisArduino = new SemanticAnalysisArduino();
        semanticAnalysisMidi = new SemanticAnalysisMidi();
        arduinoCmd = new ArduinoCmd();
        midiPlayer = new MidiPlayer();

        cmdOutput = "";

        //行号与滚动条
        scrollPane3.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_NEVER);
        scrollPane3.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        String lineStr = "";
        for (int i = 1; i < 1000; i++)
            lineStr += i + "\n";
        lineTextArea.setText(lineStr);
        scrollPane1.getVerticalScrollBar().addAdjustmentListener(e -> scrollPane3.getVerticalScrollBar().setValue(scrollPane1.getVerticalScrollBar().getValue()));

        TipsMenuItemActionPerformed(null);

        //文件拖拽直接打开
        new DropTarget(inputTextPane, DnDConstants.ACTION_COPY_OR_MOVE, new DropTargetAdapter() {
            @Override
            public void drop(DropTargetDropEvent dtde) {
                try {
                    if (dtde.isDataFlavorSupported(DataFlavor.javaFileListFlavor)) {
                        if (!showSaveComfirm("Exist unsaved content, save before open file?"))
                            return;

                        dtde.acceptDrop(DnDConstants.ACTION_COPY_OR_MOVE);
                        java.util.List<File> fileList = (java.util.List<File>) dtde.getTransferable().getTransferData(DataFlavor.javaFileListFlavor);

                        if (fileList.get(0).getName().indexOf(".mui") == -1) {
                            JOptionPane.showMessageDialog(null, "不支持的文件格式", "Warning", JOptionPane.INFORMATION_MESSAGE);
                            return;
                        }
                        BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(new FileInputStream(fileList.get(0)), "UTF-8"));
                        StringBuilder stringBuilder = new StringBuilder();
                        String content;
                        while ((content = bufferedReader.readLine()) != null) {
                            stringBuilder.append(content);
                            stringBuilder.append(System.getProperty("line.separator"));
                        }
                        bufferedReader.close();
                        inputTextPane.setText(stringBuilder.toString());
                        inputTextPane.setCaretPosition(0);
                        outputTextPane.setText("");
                        refreshColor();
                        hasSaved = true;
                        hasChanged = false;
                        setTitle("Music Interpreter - " + fileList.get(0).getName());
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        });

        //播放完成事件
        midiPlayer.getSequencer().addMetaEventListener(meta -> {
            if (meta.getType() == 47) {
                stopDirectMenuItemActionPerformed(null);
            }
        });
    }

    //内容变动调用的函数
    private void contentChanged() {
        if (hasChanged)
            return;

        hasChanged = true;
        if (this.getTitle().lastIndexOf("(Unsaved)") == -1)
            this.setTitle(this.getTitle() + " (Unsaved)");
    }

    //内容变动之后是否保存
    private boolean showSaveComfirm(String confirm) {
        if (hasChanged) {
            int exit = JOptionPane.showConfirmDialog(null, confirm, "Confirm", JOptionPane.YES_NO_CANCEL_OPTION, JOptionPane.QUESTION_MESSAGE);
            switch (exit) {
                case JOptionPane.YES_OPTION:
                    saveMenuItemActionPerformed(null);
                    break;
                case JOptionPane.NO_OPTION:
                    break;
                case JOptionPane.CANCEL_OPTION:
                    return false;
            }
        }
        return true;
    }

    //自动删除界符
    private void autoRemove() {
        StringBuilder input = new StringBuilder(inputTextPane.getText().replace("\r", ""));
        int pos = inputTextPane.getCaretPosition();
        if (input.length() > 1 && pos < input.length() && pos > 0) {
            if ((input.charAt(pos - 1) == '(' && input.charAt(pos) == ')') ||
                    (input.charAt(pos - 1) == '[' && input.charAt(pos) == ']') ||
                    (input.charAt(pos - 1) == '<' && input.charAt(pos) == '>') ||
                    (input.charAt(pos - 1) == '{' && input.charAt(pos) == '}')) {
                input.deleteCharAt(pos);
                inputTextPane.setText(input.toString());
                inputTextPane.setCaretPosition(pos);
                return;
            }
        }
    }

    //自动补全界符与注释符号
    private void autoComplete() {
        StringBuilder input = new StringBuilder(inputTextPane.getText().replace("\r", ""));
        int pos = inputTextPane.getCaretPosition();
        if (pos > 0) {
            if (pos < input.length() && (input.substring(pos, pos + 1).equals(" ") || input.substring(pos, pos + 1).equals("\n")) || pos == input.length())
                switch (input.charAt(pos - 1)) {
                    case '(':
                        input.insert(pos, ')');
                        inputTextPane.setText(input.toString());
                        inputTextPane.setCaretPosition(pos);
                        return;
                    case '[':
                        input.insert(pos, ']');
                        inputTextPane.setText(input.toString());
                        inputTextPane.setCaretPosition(pos);
                        return;
                    case '<':
                        input.insert(pos, '>');
                        inputTextPane.setText(input.toString());
                        inputTextPane.setCaretPosition(pos);
                        return;
                    case '{':
                        input.insert(pos, '}');
                        inputTextPane.setText(input.toString());
                        inputTextPane.setCaretPosition(pos);
                        return;
                    case '*':
                        if (input.charAt(pos - 2) == '/') {
                            input.insert(inputTextPane.getCaretPosition(), "\n\n*/");
                            inputTextPane.setText(input.toString());
                            inputTextPane.setCaretPosition(pos + 1);
                        }
                        return;
                }
        }
    }

    //代码着色
    private void refreshColor() {
        String input = inputTextPane.getText().replace("\r", "");

        inputStyledDocument.setCharacterAttributes(
                0,
                input.length(),
                normalAttributeSet, true
        );

        //声明着色
        Matcher statementMatcher = statementPattern.matcher(input);
        while (statementMatcher.find()) {
            inputStyledDocument.setCharacterAttributes(
                    statementMatcher.start(),
                    statementMatcher.end() - statementMatcher.start(),
                    statementAttributeSet, true
            );
        }

        //关键字着色
        Matcher inputMatcher = keywordPattern.matcher(input);
        while (inputMatcher.find()) {
            inputStyledDocument.setCharacterAttributes(
                    inputMatcher.start(),
                    inputMatcher.end() - inputMatcher.start(),
                    attributeSet, true
            );
        }

        //节奏片段着色
        Matcher parenMatcher = parenPattern.matcher(input);
        while (parenMatcher.find()) {
            inputStyledDocument.setCharacterAttributes(
                    parenMatcher.start(),
                    parenMatcher.end() - parenMatcher.start(),
                    durationAttributeSet, true
            );
        }

        //注释着色
        for (int i = 0; i < input.length(); i++) {
            //单行注释
            if (i + 1 < input.length())
                if (input.charAt(i) == '/' && input.charAt(i + 1) == '/')
                    while (i + 1 < input.length() && input.charAt(i) != '\n') {
                        i++;
                        inputStyledDocument.setCharacterAttributes(
                                i - 1,
                                2,
                                commentAttributeSet, true
                        );
                    }

            //多行注释
            if (i + 1 < input.length() && input.charAt(i) == '/' && input.charAt(i + 1) == '*')
                while (i + 1 < input.length() && (input.charAt(i) != '*' || input.charAt(i + 1) != '/')) {
                    i++;
                    inputStyledDocument.setCharacterAttributes(
                            i - 1,
                            3,
                            commentAttributeSet, true
                    );
                }
        }
    }

    //新建空文件
    private void newEmptyMenuItemActionPerformed(ActionEvent e) {
        if (showSaveComfirm("Exist unsaved content, save before new file?")) {
            hasSaved = false;

            inputTextPane.setText("");
            outputTextPane.setText("");
            hasChanged = false;
            isLoadedMidiFile = false;
            this.setTitle("Music Interpreter - New Empty File");
        }
    }

    //新建模板文件
    private void newMenuItemActionPerformed(ActionEvent e) {
        if (showSaveComfirm("Exist unsaved content, save before new file?")) {
            hasSaved = false;

            String str = "/*\n" +
                    " 数字乐谱模板\n" +
                    " 声部1 + 声部2\n" +
                    " 双声部 Version\n" +
                    " */\n" +
                    "\n" +
                    "//声部1\n" +
                    "paragraph Name1\n" +
                    "instrument= 0\n" +
                    "volume= 127\n" +
                    "speed= 90\n" +
                    "1= C\n" +
                    "1234 567[1]  <4444 4444>\n" +
                    "[1]765 4321  <4444 4444>\n" +
                    "\n" +
                    "1324    3546  <8888 8888>\n" +
                    "576[1] 7[2]1  <8888 884>\n" +
                    "\n" +
                    "[1]675 6453  <gggg gggg>\n" +
                    "4231   2(7)1  <gggg gg8>\n" +
                    "end\n" +
                    "\n" +
                    "//声部2\n" +
                    "paragraph Name2\n" +
                    "instrument= 0\n" +
                    "volume= 127\n" +
                    "speed= 90\n" +
                    "1= C\n" +
                    "1234 567[1]  <4444 4444>\n" +
                    "[1]765 4321  <4444 4444>\n" +
                    "\n" +
                    "1324    3546  <8888 8888>\n" +
                    "576[1] 7[2]1  <8888 884>\n" +
                    "\n" +
                    "[1]675 6453  <gggg gggg>\n" +
                    "4231   2(7)1  <gggg gg8>\n" +
                    "end\n" +
                    "\n" +
                    "//添加更多声部......\n" +
                    "\n" +
                    "//多声部同时播放\n" +
                    "play(Name1&Name2)";
            inputTextPane.setText(str);
            inputTextPane.setCaretPosition(0);
            refreshColor();

            outputTextPane.setText("");
            hasChanged = false;
            isLoadedMidiFile = false;
            this.setTitle("Music Interpreter - New Template File");
        }
    }

    //打开文件
    private void openMenuItemActionPerformed(ActionEvent e) {
        if (!showSaveComfirm("Exist unsaved content, save before open file?"))
            return;

        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
        FileNameExtensionFilter filter = new FileNameExtensionFilter("Music Interpreter File", "mui");
        fileChooser.setFileFilter(filter);
        int value = fileChooser.showOpenDialog(this);
        if (value == JFileChooser.CANCEL_OPTION)
            return;
        file = fileChooser.getSelectedFile();
        try {
            BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(new FileInputStream(file), "UTF-8"));
            StringBuilder stringBuilder = new StringBuilder();
            String content;
            while ((content = bufferedReader.readLine()) != null) {
                stringBuilder.append(content);
                stringBuilder.append(System.getProperty("line.separator"));
            }
            bufferedReader.close();
            inputTextPane.setText(stringBuilder.toString());
            inputTextPane.setCaretPosition(0);
            outputTextPane.setText("");
            refreshColor();
            hasSaved = true;
            hasChanged = false;
            stopDirectMenuItemActionPerformed(null);
            isLoadedMidiFile = false;
            this.setTitle("Music Interpreter - " + file.getName());
        } catch (FileNotFoundException e1) {
//            e1.printStackTrace();
        } catch (IOException e1) {
//            e1.printStackTrace();
        }
    }

    //保存文件
    private void saveMenuItemActionPerformed(ActionEvent e) {
        if (!hasSaved) {
            saveAsMenuItemActionPerformed(null);
        } else {
            try {
                if (!file.exists())
                    file.createNewFile();
                BufferedWriter bufferedWriter = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(file), "UTF-8"));
                bufferedWriter.write(inputTextPane.getText());
                bufferedWriter.close();
                hasChanged = false;
                isLoadedMidiFile = false;
                this.setTitle("Music Interpreter - " + file.getName());
                stopDirectMenuItemActionPerformed(null);
                isLoadedMidiFile = false;
            } catch (IOException e1) {
//                e1.printStackTrace();
            }
        }
    }

    //另存为文件
    private void saveAsMenuItemActionPerformed(ActionEvent e) {
        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
        FileNameExtensionFilter filter = new FileNameExtensionFilter("Music Interpreter File", "mui");
        fileChooser.setFileFilter(filter);
        int value = fileChooser.showSaveDialog(this);
        if (value == JFileChooser.CANCEL_OPTION)
            return;
        String fileStr = fileChooser.getSelectedFile().getAbsoluteFile().toString();
        if (fileStr.lastIndexOf(".mui") == -1)
            fileStr += ".mui";
        file = new File(fileStr);
        try {
            if (!file.exists())
                file.createNewFile();
            BufferedWriter bufferedWriter = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(file), "UTF-8"));
            bufferedWriter.write(inputTextPane.getText());
            bufferedWriter.close();
            hasSaved = true;
            hasChanged = false;
            isLoadedMidiFile = false;
            this.setTitle("Music Interpreter - " + file.getName());
            stopDirectMenuItemActionPerformed(null);
            isLoadedMidiFile = false;
        } catch (FileNotFoundException e1) {
//            e1.printStackTrace();
        } catch (IOException e1) {
//            e1.printStackTrace();
        }
    }

    //通过行号找到改行第一个字符在输入字符串中的位置
    private int getIndexByLine(int line) {
        int index = 0;
        String input = inputTextPane.getText().replace("\r", "") + "\n";

        for (int i = 0; i < line - 1; i++) {
            index = input.indexOf("\n", index + 1);
        }
        return index;
    }

    //词法分析
    private ArrayList<Token> runLex(String input, StringBuilder output) {
        lexicalAnalysis.Lex(input);
        ArrayList<Token> tokens = lexicalAnalysis.getTokens();

        if (lexicalAnalysis.getError()) {
            output.append(lexicalAnalysis.getErrorInfo(tokens));
            output.append("检测到词法错误，分析停止");
            outputTextPane.setText(output.toString());
            for (int line : lexicalAnalysis.getErrorLine()) {
                inputStyledDocument.setCharacterAttributes(
                        getIndexByLine(line),
                        getIndexByLine(line + 1) - getIndexByLine(line),
                        errorAttributeSet, true
                );
            }
            return null;
        } else
            for (Token token : tokens)
                output.append(token);

        return tokens;
    }

    //语法分析
    private Node runSyn(ArrayList<Token> tokens, StringBuilder output) {
        Node AbstractSyntaxTree = syntacticAnalysis.Parse(tokens);

        if (syntacticAnalysis.getIsError()) {
            output.append(syntacticAnalysis.getErrors(AbstractSyntaxTree));
            output.append("\n检测到语法错误，分析停止\n");
            outputTextPane.setText(output.toString());
            for (int line : syntacticAnalysis.getErrorList()) {
                inputStyledDocument.setCharacterAttributes(
                        getIndexByLine(line),
                        getIndexByLine(line + 1) - getIndexByLine(line),
                        errorAttributeSet, true
                );
            }
            return null;
        } else
            output.append(AbstractSyntaxTree.print(0));

        return AbstractSyntaxTree;
    }

    //Arduino语义分析
    private String runArduinoSem(Node abstractSyntaxTree, StringBuilder output) {
        String code = semanticAnalysisArduino.ConvertToArduino(abstractSyntaxTree);

        if (semanticAnalysisArduino.getIsError()) {
            output.append(semanticAnalysisArduino.getErrors());
            output.append("\n检测到语义错误，分析停止\n");
            outputTextPane.setText(output.toString());
            for (int line : semanticAnalysisArduino.getErrorLines()) {
                inputStyledDocument.setCharacterAttributes(
                        getIndexByLine(line),
                        getIndexByLine(line + 1) - getIndexByLine(line),
                        errorAttributeSet, true
                );
            }
            return null;
        } else {
            output.append(code);
        }

        return code;
    }

    //Midi语义分析
    private String runMidiSem(Node abstractSyntaxTree, StringBuilder output) {
        String code = semanticAnalysisMidi.ConvertToMidi(abstractSyntaxTree);

        if (semanticAnalysisMidi.getIsError()) {
            output.append(semanticAnalysisMidi.getErrors());
            output.append("\n检测到语义错误，分析停止\n");
            outputTextPane.setText(output.toString());
            for (int line : semanticAnalysisMidi.getErrorLines()) {
                inputStyledDocument.setCharacterAttributes(
                        getIndexByLine(line),
                        getIndexByLine(line + 1) - getIndexByLine(line),
                        errorAttributeSet, true
                );
            }
            return null;
        } else {
            output.append(code);
        }

        return code;
    }

    //执行词法分析
    private void LexMenuItemActionPerformed(ActionEvent e) {
        StringBuilder stringBuilder = new StringBuilder();

        if (inputTextPane.getText().isEmpty())
            return;

        runLex(inputTextPane.getText(), stringBuilder);

        outputTextPane.setText(stringBuilder.toString());
    }

    //执行语法分析
    private void synMenuItemActionPerformed(ActionEvent e) {
        StringBuilder stringBuilder = new StringBuilder();


        if (inputTextPane.getText().isEmpty())
            return;

        ArrayList<Token> tokens = runLex(inputTextPane.getText(), stringBuilder);

        if (tokens == null)
            return;

        stringBuilder.append("\n=======词法分析结束======开始语法分析=======\n\n");

        runSyn(tokens, stringBuilder);

        outputTextPane.setText(stringBuilder.toString());
    }

    //执行Arduino语义分析
    private void semMenuItemActionPerformed(ActionEvent e) {
        StringBuilder stringBuilder = new StringBuilder();

        if (inputTextPane.getText().isEmpty())
            return;

        ArrayList<Token> tokens = runLex(inputTextPane.getText(), stringBuilder);

        if (tokens == null)
            return;

        stringBuilder.append("\n=======词法分析结束======开始语法分析=======\n\n");

        Node AbstractSyntaxTree = runSyn(tokens, stringBuilder);

        if (AbstractSyntaxTree == null)
            return;

        stringBuilder.append("\n=======语法分析结束======开始语义分析=======\n\n");

        runArduinoSem(AbstractSyntaxTree, stringBuilder);

        outputTextPane.setText(stringBuilder.toString());
    }

    //执行Midi语义分析
    private void sem2MenuItemActionPerformed(ActionEvent e) {
        StringBuilder stringBuilder = new StringBuilder();

        if (inputTextPane.getText().isEmpty())
            return;

        ArrayList<Token> tokens = runLex(inputTextPane.getText(), stringBuilder);

        if (tokens == null)
            return;

        stringBuilder.append("\n=======词法分析结束======开始语法分析=======\n\n");

        Node AbstractSyntaxTree = runSyn(tokens, stringBuilder);

        if (AbstractSyntaxTree == null)
            return;

        stringBuilder.append("\n=======语法分析结束======开始语义分析=======\n\n");

        runMidiSem(AbstractSyntaxTree, stringBuilder);

        outputTextPane.setText(stringBuilder.toString());
    }

    //生成Midi文件
    private void generateMidiMenuItemActionPerformed(ActionEvent e) {
        StringBuilder stringBuilder = new StringBuilder();

        if (inputTextPane.getText().isEmpty())
            return;

        ArrayList<Token> tokens = runLex(inputTextPane.getText(), stringBuilder);

        if (tokens == null)
            return;

        stringBuilder.append("\n=======词法分析结束======开始语法分析=======\n\n");

        Node AbstractSyntaxTree = runSyn(tokens, stringBuilder);

        if (AbstractSyntaxTree == null)
            return;

        stringBuilder.append("\n=======语法分析结束======开始语义分析=======\n\n");

        String code = runMidiSem(AbstractSyntaxTree, stringBuilder);

        if (code == null)
            return;

        outputTextPane.setText(code + "\n\n===========================================\nMidi Successfully Generated");

        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
        FileNameExtensionFilter filter = new FileNameExtensionFilter("Midi File", "mid");
        fileChooser.setFileFilter(filter);
        int value = fileChooser.showSaveDialog(this);
        if (value == JFileChooser.CANCEL_OPTION)
            return;
        String fileStr = fileChooser.getSelectedFile().getAbsoluteFile().toString();
        if (fileStr.lastIndexOf(".mid") == -1)
            fileStr += ".mid";
        midiFile = new File(fileStr);

        if (!semanticAnalysisMidi.getMidiFile().writeToFile(midiFile))
            JOptionPane.showMessageDialog(this, "目标文件被占用，无法导出", "Warning", JOptionPane.INFORMATION_MESSAGE);

    }

    //保存Arduino执行文件
    private void buildMenuItemActionPerformed(ActionEvent e) {
        StringBuilder stringBuilder = new StringBuilder();

        if (inputTextPane.getText().isEmpty())
            return;

        ArrayList<Token> tokens = runLex(inputTextPane.getText(), stringBuilder);

        if (tokens == null)
            return;

        stringBuilder.append("\n=======词法分析结束======开始语法分析=======\n\n");

        Node AbstractSyntaxTree = runSyn(tokens, stringBuilder);

        if (AbstractSyntaxTree == null)
            return;

        stringBuilder.append("\n=======语法分析结束======开始语义分析=======\n\n");

        String code = runArduinoSem(AbstractSyntaxTree, stringBuilder);

        if (code == null)
            return;

        outputTextPane.setText(code);
        outputTextPane.setCaretPosition(0);

        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
        FileNameExtensionFilter filter = new FileNameExtensionFilter("Arduino File", "ino");
        fileChooser.setFileFilter(filter);
        int value = fileChooser.showSaveDialog(this);
        if (value == JFileChooser.CANCEL_OPTION)
            return;
        String fileStr = fileChooser.getSelectedFile().getAbsoluteFile().toString();
        if (fileStr.lastIndexOf(".ino") == -1)
            fileStr += ".ino";
        inoFile = new File(fileStr);
        try {
            if (!inoFile.exists())
                inoFile.createNewFile();
            BufferedWriter bufferedWriter = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(inoFile), "UTF-8"));
            bufferedWriter.write(code);
            bufferedWriter.close();
        } catch (FileNotFoundException e1) {
//            e1.printStackTrace();
        } catch (IOException e1) {
//            e1.printStackTrace();
        }

    }

    //读取Arduino CMD数据流
    private void readCmd() {
        compileMenuItem.setEnabled(false);
        uploadMenuItem.setEnabled(false);
        progressBar.setIndeterminate(true);
        cmdOutput = "";

        //处理输出的线程
        new Thread(() -> {
            int count = 0;
            try {
                BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(ArduinoCmd.output, "GBK"));
                String tempStr;
                while ((tempStr = bufferedReader.readLine()) != null) {
                    count++;
                    if (count > 12) {
                        cmdOutput += tempStr + "\n";
                        outputTextPane.setText(cmdOutput);
                    }
                }
                progressBar.setIndeterminate(false);
                progressBar.setValue(100);
                compileMenuItem.setEnabled(true);
                uploadMenuItem.setEnabled(true);
            } catch (IOException IOE) {
                IOE.printStackTrace();
            } finally {
                try {
                    ArduinoCmd.output.close();
                } catch (IOException IOE) {
                    IOE.printStackTrace();
                }
            }
        }).start();

        //处理错误信息的线程
        new Thread(() -> {
            try {
                BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(ArduinoCmd.error, "GBK"));
                String tempStr;
                while ((tempStr = bufferedReader.readLine()) != null) {
                    cmdOutput += tempStr + "\n";
                    outputTextPane.setText(cmdOutput);
                }
            } catch (IOException IOE) {
                IOE.printStackTrace();
            } finally {
                try {
                    ArduinoCmd.error.close();
                } catch (IOException IOE) {
                    IOE.printStackTrace();
                }
            }
        }).start();
    }

    //编译Arduino的十六进制文件
    private void compileMenuItemActionPerformed(ActionEvent e) {
        StringBuilder stringBuilder = new StringBuilder();

        if (inputTextPane.getText().isEmpty())
            return;

        ArrayList<Token> tokens = runLex(inputTextPane.getText(), stringBuilder);

        if (tokens == null)
            return;

        stringBuilder.append("\n=======词法分析结束======开始语法分析=======\n\n");

        Node AbstractSyntaxTree = runSyn(tokens, stringBuilder);

        if (AbstractSyntaxTree == null)
            return;

        stringBuilder.append("\n=======语法分析结束======开始语义分析=======\n\n");

        String code = runArduinoSem(AbstractSyntaxTree, stringBuilder);

        if (code == null)
            return;

        File tempFile;

        try {
            tempFile = new File("C:\\Users\\Chief\\Documents\\Arduino\\temp.ino");
            if (!tempFile.exists())
                tempFile.createNewFile();
            BufferedWriter bufferedWriter = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(tempFile), "UTF-8"));
            bufferedWriter.write(code);
            bufferedWriter.close();

            arduinoCmd.compile(tempFile.getAbsolutePath());
            readCmd();

        } catch (IOException e1) {
//                e1.printStackTrace();
        }
    }

    //上传到Arduino
    private void uploadMenuItemActionPerformed(ActionEvent e) {
        StringBuilder stringBuilder = new StringBuilder();

        if (inputTextPane.getText().isEmpty())
            return;

        ArrayList<Token> tokens = runLex(inputTextPane.getText(), stringBuilder);

        if (tokens == null)
            return;

        stringBuilder.append("\n=======词法分析结束======开始语法分析=======\n\n");

        Node AbstractSyntaxTree = runSyn(tokens, stringBuilder);

        if (AbstractSyntaxTree == null)
            return;

        stringBuilder.append("\n=======语法分析结束======开始语义分析=======\n\n");

        String code = runArduinoSem(AbstractSyntaxTree, stringBuilder);

        if (code == null)
            return;

        File tempFile;

        try {
            tempFile = new File("C:\\Users\\Chief\\Documents\\Arduino\\temp.ino");
            if (!tempFile.exists())
                tempFile.createNewFile();
            BufferedWriter bufferedWriter = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(tempFile), "UTF-8"));
            bufferedWriter.write(code);
            bufferedWriter.close();

            arduinoCmd.upload(tempFile.getAbsolutePath());
            readCmd();
        } catch (IOException e1) {
//                e1.printStackTrace();
        }
    }

    //生成临时Midi文件
    private boolean generateTempMidiFile() {
        StringBuilder stringBuilder = new StringBuilder();

        if (inputTextPane.getText().isEmpty())
            return false;

        ArrayList<Token> tokens = runLex(inputTextPane.getText(), stringBuilder);

        if (tokens == null)
            return false;

        stringBuilder.append("\n=======词法分析结束======开始语法分析=======\n\n");

        Node AbstractSyntaxTree = runSyn(tokens, stringBuilder);

        if (AbstractSyntaxTree == null)
            return false;

        stringBuilder.append("\n=======语法分析结束======开始语义分析=======\n\n");

        String code = runMidiSem(AbstractSyntaxTree, stringBuilder);

        if (code == null)
            return false;

        outputTextPane.setText(code + "\n\n===========================================\nMidi Successfully Generated");

        if (tempMidiFile == null) {
            tempMidiFile = new File("tempMidi.mid");
        }

        if (!semanticAnalysisMidi.getMidiFile().writeToFile(tempMidiFile)) {
            JOptionPane.showMessageDialog(this, "目标文件被占用，无法导出", "Warning", JOptionPane.INFORMATION_MESSAGE);
            return false;
        }

        return true;
    }

    //直接播放Midi文件
    private void playMenuItemActionPerformed(ActionEvent e) {
        if (!generateTempMidiFile())
            return;

        try {
            Runtime.getRuntime().exec("rundll32 url.dll FileProtocolHandler file://" + tempMidiFile.getAbsolutePath().replace("\\", "\\\\"));
        } catch (IOException e1) {
            e1.printStackTrace();
        }
    }

    //读取SoundFont
    private void loadSoundFontMenuItemActionPerformed(ActionEvent e) {
        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
        FileNameExtensionFilter filter = new FileNameExtensionFilter("SoundFont File", "sf2", "sf3");
        fileChooser.setFileFilter(filter);
        int value = fileChooser.showOpenDialog(this);
        if (value == JFileChooser.CANCEL_OPTION)
            return;
        File soundFontFile = fileChooser.getSelectedFile();
        midiPlayer.loadSoundBank(soundFontFile);
    }

    //直接播放Midi按钮
    private void playDirectMenuItemActionPerformed(ActionEvent e) {
        if (!isLoadedMidiFile) {
            if (!generateTempMidiFile())
                return;

            midiPlayer.loadMidiFile(tempMidiFile);
            isLoadedMidiFile = true;
        }

        if (midiPlayer.getIsRunning()) {
            midiPlayer.pause();
            playDirectMenuItem.setText("Replay");
        } else {
            midiPlayer.play();
            playDirectMenuItem.setText("Pause");
        }
    }

    //停止直接播放Midi按钮
    private void stopDirectMenuItemActionPerformed(ActionEvent e) {
        playDirectMenuItem.setText("Play Midi with SoundFont");
        midiPlayer.stop();
    }

    //关于
    private void aboutMenuItemActionPerformed(ActionEvent e) {
        String str = "============================================\n" +
                "\t               Music Language Interpreter\n" +
                "\n" +
                "\t       Designed: Chief, yyzih and AsrielMao\n" +
                "\n" +
                "\t\t  Current Version: 3.0.1\n" +
                "\n" +
                "\n" +
                "    The Music Language Interpreter is a light weight interpreter,\n" +
                "\n" +
                "which contains complete lexical analysis, syntactic analysis and\n" +
                "\n" +
                "semantic analysis, for converting digit score to Arduino code or\n" +
                "\n" +
                "Midi file. \n" +
                "\n" +
                "    The Music Language Interpreter also contains a simple Midi\n" +
                "\n" +
                "player for playing midi file with SoundFont2 or SoundFont3.\n" +
                "\n" +
                "\n" +
                "\n" +
                "\n" +
                "\n" +
                "\n" +
                "\n" +
                "\n" +
                "\n" +
                "\t\t\t\t   All Rights Reserved. \n" +
                "\n" +
                "    \t    Copyright © 2018-2020 Chief, yyzih and AsrielMao.\n" +
                "============================================";
        outputTextPane.setText(str);
        outputTextPane.setCaretPosition(0);
    }

    //展示Demo
    private void demoMenuItemActionPerformed(ActionEvent e) {
        if (!showSaveComfirm("Exist unsaved content, save before open the demo?"))
            return;

        String str = "/*\n" +
                " 欢乐颂\n" +
                " 女高音 + 女中音\n" +
                " 双声部 Version\n" +
                " */\n" +
                "\n" +
                "//女高音\n" +
                "paragraph soprano\n" +
                "instrument= 0\n" +
                "volume= 127\n" +
                "speed= 140\n" +
                "1= D\n" +
                "3345 5432 <4444 4444>\n" +
                "1123 322 <4444 4*82>\n" +
                "3345 5432 <4444 4444>\n" +
                "1123 211 <4444 4*82>\n" +
                "2231 23431 <4444 4{88}44>\n" +
                "23432 12(5) <4{88}44 {44}4>\n" +
                "33345 54342 <{44}444 44{48}8>\n" +
                "1123 211 <4444 4*82>\n" +
                "end\n" +
                "\n" +
                "//女中音\n" +
                "paragraph alto\n" +
                "instrument= 0\n" +
                "volume= 110\n" +
                "speed= 140\n" +
                "1= D\n" +
                "1123 321(5) <4444 4444>\n" +
                "(3555) 1(77) <4444 4*82>\n" +
                "1123 321(5) <4444 4444>\n" +
                "(3555) (533) <4444 4*82>\n" +
                "(77)1(5) (77)1(5) <4444 4444>\n" +
                "(7#5#5#56#45) <4444 {44}4>\n" +
                "11123 3211(5) <{44}444 44{48}8>\n" +
                "(3555 533) <4444 4*82>\n" +
                "end\n" +
                "\n" +
                "//双声部同时播放\n" +
                "play(soprano&alto)";
        inputTextPane.setText(str);
        inputTextPane.setCaretPosition(0);
        refreshColor();
        hasChanged = false;
        this.setTitle("Music Interpreter - Demo");
        TipsMenuItemActionPerformed(null);
    }

    //显示提示
    private void TipsMenuItemActionPerformed(ActionEvent e) {
        String str = "============================================\n" +
                "                                                  Tips\n" +
                "-------------------------------------------------------------------------\n" +
                "* 你可以在“Help-Tips”中随时打开Tips\n" +
                "\n" +
                "1. 构成乐谱的成分：\n" +
                "\t1）paragraph Name  声部声明\n" +
                "\t2）instrument= 0      \t演奏的乐器（非必要 默认钢琴）\n" +
                "\t3）volume= 127        该声部的音量（非必要 默认127）\n" +
                "\t4）speed= 90\t该声部演奏速度（非必要 默认90）\n" +
                "\t5）1= C\t\t该声部调性（非必要 默认C调）\n" +
                "\t6）((1))(2)3[4][[5]]\t音符的音名，即音高\n" +
                "\t7）<1248gw*>\t音符的时值，即持续时间\n" +
                "\t8）end\t\t声部声明结束\n" +
                "\t（其中2~3，即乐器与音量只对Midi有效）\n" +
                "\n" +
                "2. 乐谱成分的解释：\n" +
                "\t1）声部声明：标识符须以字母开头，后跟字母或数字\n" +
                "\t2）乐器音色：见“Help-Instrument”中具体说明\n" +
                "\t3）声部音量：最小值0（禁音）最大值127（最大音量）\n" +
                "\t4）声部速度：每分钟四分音符个数，即BPM\n" +
                "\t5）声部调性：CDEFGAB加上b（降号）与#（升号）\n" +
                "\t6）“( )”内为低八度，可叠加“[ ]”内为高八度，同上\n" +
                "\t7）“< >”内为全、2、4、6、8、16、32分音符与附点\n" +
                "\t（Arduino可以使用{ }表示连音，Midi暂不支持）\n" +
                "\t8）声明结束：须用end结束声明，对应paragraph\n" +
                "\n" +
                "3. 播放乐谱的方法：\n" +
                "\t1）通过“play( )”进行播放，( )”内为声部的标识符\n" +
                "\t2）“&”左右的声部将同时播放，\n" +
                "\t3）“ , ”左右的声部将先后播放\n" +
                "============================================";
        outputTextPane.setText(str);
        outputTextPane.setCaretPosition(0);
    }

    //显示乐器列表
    private void InstruMenuItemActionPerformed(ActionEvent e) {
        String str = "===========================================\n" +
                "                                            Instrument\n" +
                "-----------------------------------------------------------------------\n" +
                "音色号\t乐器名\t                |\t音色号\t乐器名\n" +
                "-----------------------------------------------------------------------\n" +
                "钢琴类\t\t                |\t簧乐器\n" +
                "0（推荐）\t大钢琴\t                |\t64\t高音萨克斯\n" +
                "1\t亮音钢琴\t                |\t65\t中音萨克斯\n" +
                "2\t电子大钢琴\t                |\t66\t次中音萨克斯\n" +
                "3\t酒吧钢琴\t                |\t67\t上低音萨克斯\n" +
                "4\t电钢琴1\t                |\t68\t双簧管\n" +
                "5\t电钢琴2\t                |\t69\t英国管\n" +
                "6\t大键琴\t                |\t70\t巴颂管\n" +
                "7\t电翼琴\t                |\t71\t单簧管\n" +
                "-----------------------------------------------------------------------\n" +
                "固定音高敲击乐器\t                |\t吹管乐器\n" +
                "8\t钢片琴\t                |\t72\t短笛\n" +
                "9\t钟琴\t                |\t73\t长笛\n" +
                "10（推荐）音乐盒\t                |\t74\t竖笛\n" +
                "11\t颤音琴\t                |\t75（推荐）牧笛\n" +
                "12\t马林巴琴\t                |\t76\t瓶笛\n" +
                "13\t木琴\t                |\t77\t尺八\n" +
                "14\t管钟\t                |\t78\t哨子\n" +
                "15\t洋琴\t                |\t79\t陶笛\n" +
                "-----------------------------------------------------------------------\n" +
                "风琴\t\t                |\t合成音主旋律\n" +
                "16\t音栓风琴\t                |\t80\t方波\n" +
                "17\t敲击风琴\t                |\t81\t锯齿波\n" +
                "18\t摇滚风琴\t                |\t82\t汽笛风琴\n" +
                "19\t教堂管风琴\t                |\t83\t合成吹管\n" +
                "20\t簧风琴\t                |\t84\t合成电吉他\n" +
                "21（推荐）手风琴\t                |\t85\t人声键\n" +
                "22\t口琴\t                |\t86\t五度音\n" +
                "23（推荐）探戈手风琴\t                |\t87\t贝斯吉他合奏\n" +
                "-----------------------------------------------------------------------\n" +
                "吉他\t\t                |\t合成音和弦衬底\n" +
                "24（推荐）木吉他（尼龙弦）      |\t88\t新时代\n" +
                "25\t木吉他（钢弦）          |\t89\t温暖的\n" +
                "26\t电吉他（爵士）          |\t90\t多重和音\n" +
                "27\t电吉他（清音）          |\t91\t唱诗班\n" +
                "28\t电吉他（闷音）          |\t92\t弓弦音色\n" +
                "29\t电吉他（驱动音效）   |\t93\t金属的\n" +
                "30\t电吉他（失真音效）   |\t94\t光华\n" +
                "31\t吉他泛音\t                |\t95\t宽阔的\n" +
                "-----------------------------------------------------------------------\n" +
                "贝斯\t\t                |\t合成音效果\n" +
                "32（推荐）贝斯\t                |\t96\t雨声\n" +
                "33\t电贝斯（指弹）          |\t97\t电影音效\n" +
                "34\t电贝斯（拨片）          |\t98\t水晶\n" +
                "35\t无品贝斯\t                |\t99\t气氛\n" +
                "36\t打弦贝斯1\t                |\t100\t明亮\n" +
                "37\t打弦贝斯2\t                |\t101\t魅影\n" +
                "38\t合成贝斯1\t                |\t102\t回音\n" +
                "39\t合成贝斯2\t                |\t103\t科幻\n" +
                "-----------------------------------------------------------------------\n" +
                "弦乐器\t\t                |\t民族乐器\n" +
                "40\t小提琴\t                |\t104\t西塔琴\n" +
                "41\t中提琴\t                |\t105\t斑鸠琴\n" +
                "42\t大提琴\t                |\t106\t三味线\n" +
                "43\t低音提琴\t                |\t107\t古筝\n" +
                "44\t颤弓弦乐\t                |\t108\t卡林巴铁片琴\n" +
                "45\t弹拨弦乐\t                |\t109\t苏格兰风琴\n" +
                "46\t竖琴\t                |\t110\t古提亲\n" +
                "47\t定音鼓\t                |\t111\t兽笛\n" +
                "-----------------------------------------------------------------------\n" +
                "合奏\t\t                |\t打击乐器\n" +
                "48\t弦乐合奏1\t                |\t112\t叮当铃\n" +
                "49\t弦乐合奏2\t                |\t113\t阿果果鼓\n" +
                "50\t合成弦乐1\t                |\t114\t钢鼓\n" +
                "51\t合成弦乐2\t                |\t115\t木鱼\n" +
                "52\t唱诗班“啊”             |\t116\t太鼓\n" +
                "53\t合唱“喔”\t                |\t117\t定音筒鼓\n" +
                "54\t合成人声\t                |\t118\t合成鼓\n" +
                "55\t交响打击乐\t                |\t119\t反钹\n" +
                "-----------------------------------------------------------------------\n" +
                "铜管乐器\t\t                |\t特殊音效\n" +
                "56\t小号\t                |\t120\t吉他滑弦杂音\n" +
                "57\t长号\t                |\t121\t呼吸杂音\n" +
                "58\t大号\t                |\t122\t海浪\n" +
                "59\t闷音小号\t                |\t123\t鸟鸣\n" +
                "60\t法国圆号\t                |\t124\t电话铃声\n" +
                "61\t铜管乐\t                |\t125\t直升机\n" +
                "62\t合成铜管1\t                |\t126\t鼓掌\n" +
                "63\t合成铜管2\t                |\t127\t枪声\n" +
                "===========================================";
        outputTextPane.setText(str);
        outputTextPane.setCaretPosition(0);
    }

    private void initComponents() {
        // JFormDesigner - Component initialization - DO NOT MODIFY  //GEN-BEGIN:initComponents
        menuBar1 = new JMenuBar();
        fileMenu = new JMenu();
        newEmptyMenuItem = new JMenuItem();
        newMenuItem = new JMenuItem();
        openMenuItem = new JMenuItem();
        saveMenuItem = new JMenuItem();
        saveAsMenuItem = new JMenuItem();
        runMenu = new JMenu();
        LexMenuItem = new JMenuItem();
        synMenuItem = new JMenuItem();
        semMenuItem = new JMenuItem();
        sem2MenuItem = new JMenuItem();
        buildMenu = new JMenu();
        buildMenuItem = new JMenuItem();
        compileMenuItem = new JMenuItem();
        uploadMenuItem = new JMenuItem();
        buildMidiMenu = new JMenu();
        generateMidiMenuItem = new JMenuItem();
        playMenuItem = new JMenuItem();
        loadSoundFontMenuItem = new JMenuItem();
        playDirectMenuItem = new JMenuItem();
        stopDirectMenuItem = new JMenuItem();
        helpMenu = new JMenu();
        InstruMenuItem = new JMenuItem();
        TipsMenuItem = new JMenuItem();
        demoMenuItem = new JMenuItem();
        aboutMenuItem = new JMenuItem();
        hSpacer1 = new JPanel(null);
        progressBar = new JProgressBar();
        hSpacer2 = new JPanel(null);
        panel1 = new JPanel();
        scrollPane3 = new JScrollPane();
        lineTextArea = new JTextArea();
        scrollPane1 = new JScrollPane();
        inputTextPane = new JTextPane();
        scrollPane2 = new JScrollPane();
        outputTextPane = new JTextPane();

        //======== this ========
        setFont(new Font("Microsoft YaHei UI", Font.PLAIN, 12));
        setTitle("Music Interpreter - New File");
        setMinimumSize(new Dimension(916, 709));
        Container contentPane = getContentPane();
        contentPane.setLayout(new GridLayout());

        //======== menuBar1 ========
        {

            //======== fileMenu ========
            {
                fileMenu.setText("File");

                //---- newEmptyMenuItem ----
                newEmptyMenuItem.setText("New - Empty");
                newEmptyMenuItem.addActionListener(e -> newEmptyMenuItemActionPerformed(e));
                fileMenu.add(newEmptyMenuItem);

                //---- newMenuItem ----
                newMenuItem.setText("New - Template");
                newMenuItem.addActionListener(e -> newMenuItemActionPerformed(e));
                fileMenu.add(newMenuItem);

                //---- openMenuItem ----
                openMenuItem.setText("Open");
                openMenuItem.addActionListener(e -> openMenuItemActionPerformed(e));
                fileMenu.add(openMenuItem);

                //---- saveMenuItem ----
                saveMenuItem.setText("Save");
                saveMenuItem.addActionListener(e -> saveMenuItemActionPerformed(e));
                fileMenu.add(saveMenuItem);

                //---- saveAsMenuItem ----
                saveAsMenuItem.setText("Save As...");
                saveAsMenuItem.addActionListener(e -> saveAsMenuItemActionPerformed(e));
                fileMenu.add(saveAsMenuItem);
            }
            menuBar1.add(fileMenu);

            //======== runMenu ========
            {
                runMenu.setText("Run");

                //---- LexMenuItem ----
                LexMenuItem.setText("Lexical Analysis");
                LexMenuItem.addActionListener(e -> LexMenuItemActionPerformed(e));
                runMenu.add(LexMenuItem);
                runMenu.addSeparator();

                //---- synMenuItem ----
                synMenuItem.setText("Syntactic Analysis");
                synMenuItem.addActionListener(e -> synMenuItemActionPerformed(e));
                runMenu.add(synMenuItem);
                runMenu.addSeparator();

                //---- semMenuItem ----
                semMenuItem.setText("Semantic Analysis - Arduino");
                semMenuItem.addActionListener(e -> semMenuItemActionPerformed(e));
                runMenu.add(semMenuItem);

                //---- sem2MenuItem ----
                sem2MenuItem.setText("Semantic Analysis - Midi");
                sem2MenuItem.addActionListener(e -> sem2MenuItemActionPerformed(e));
                runMenu.add(sem2MenuItem);
            }
            menuBar1.add(runMenu);

            //======== buildMenu ========
            {
                buildMenu.setText("Arduino");

                //---- buildMenuItem ----
                buildMenuItem.setText("Generate .ino file");
                buildMenuItem.addActionListener(e -> buildMenuItemActionPerformed(e));
                buildMenu.add(buildMenuItem);

                //---- compileMenuItem ----
                compileMenuItem.setText("Compile / Verify");
                compileMenuItem.addActionListener(e -> compileMenuItemActionPerformed(e));
                buildMenu.add(compileMenuItem);

                //---- uploadMenuItem ----
                uploadMenuItem.setText("Upload to Arduino");
                uploadMenuItem.addActionListener(e -> uploadMenuItemActionPerformed(e));
                buildMenu.add(uploadMenuItem);
            }
            menuBar1.add(buildMenu);

            //======== buildMidiMenu ========
            {
                buildMidiMenu.setText("Midi");

                //---- generateMidiMenuItem ----
                generateMidiMenuItem.setText("Generate Midi File");
                generateMidiMenuItem.addActionListener(e -> generateMidiMenuItemActionPerformed(e));
                buildMidiMenu.add(generateMidiMenuItem);

                //---- playMenuItem ----
                playMenuItem.setText("Play Midi File");
                playMenuItem.addActionListener(e -> playMenuItemActionPerformed(e));
                buildMidiMenu.add(playMenuItem);
                buildMidiMenu.addSeparator();

                //---- loadSoundFontMenuItem ----
                loadSoundFontMenuItem.setText("Load SoundFont");
                loadSoundFontMenuItem.addActionListener(e -> loadSoundFontMenuItemActionPerformed(e));
                buildMidiMenu.add(loadSoundFontMenuItem);

                //---- playDirectMenuItem ----
                playDirectMenuItem.setText("Play Midi with SoundFont");
                playDirectMenuItem.addActionListener(e -> playDirectMenuItemActionPerformed(e));
                buildMidiMenu.add(playDirectMenuItem);

                //---- stopDirectMenuItem ----
                stopDirectMenuItem.setText("Stop");
                stopDirectMenuItem.addActionListener(e -> stopDirectMenuItemActionPerformed(e));
                buildMidiMenu.add(stopDirectMenuItem);
            }
            menuBar1.add(buildMidiMenu);

            //======== helpMenu ========
            {
                helpMenu.setText("Help");

                //---- InstruMenuItem ----
                InstruMenuItem.setText("Instrument");
                InstruMenuItem.addActionListener(e -> InstruMenuItemActionPerformed(e));
                helpMenu.add(InstruMenuItem);

                //---- TipsMenuItem ----
                TipsMenuItem.setText("Tips");
                TipsMenuItem.addActionListener(e -> TipsMenuItemActionPerformed(e));
                helpMenu.add(TipsMenuItem);

                //---- demoMenuItem ----
                demoMenuItem.setText("Demo");
                demoMenuItem.addActionListener(e -> demoMenuItemActionPerformed(e));
                helpMenu.add(demoMenuItem);

                //---- aboutMenuItem ----
                aboutMenuItem.setText("About");
                aboutMenuItem.addActionListener(e -> aboutMenuItemActionPerformed(e));
                helpMenu.add(aboutMenuItem);
            }
            menuBar1.add(helpMenu);

            //---- hSpacer1 ----
            hSpacer1.setMaximumSize(new Dimension(1920, 32767));
            hSpacer1.setPreferredSize(new Dimension(150, 20));
            hSpacer1.setMinimumSize(new Dimension(150, 12));
            menuBar1.add(hSpacer1);

            //---- progressBar ----
            progressBar.setMaximumSize(new Dimension(150, 20));
            progressBar.setMinimumSize(new Dimension(150, 20));
            progressBar.setPreferredSize(new Dimension(150, 20));
            progressBar.setFocusable(false);
            progressBar.setRequestFocusEnabled(false);
            menuBar1.add(progressBar);

            //---- hSpacer2 ----
            hSpacer2.setMaximumSize(new Dimension(20, 32767));
            hSpacer2.setMinimumSize(new Dimension(20, 12));
            hSpacer2.setPreferredSize(new Dimension(20, 10));
            menuBar1.add(hSpacer2);
        }
        setJMenuBar(menuBar1);

        //======== panel1 ========
        {
            panel1.setLayout(new MigLayout(
                "insets 0,hidemode 3",
                // columns
                "[fill]0" +
                "[400:400:875,grow,fill]0" +
                "[460:460:1005,grow,fill]",
                // rows
                "[fill]"));

            //======== scrollPane3 ========
            {

                //---- lineTextArea ----
                lineTextArea.setFont(new Font("Microsoft YaHei", Font.PLAIN, 14));
                lineTextArea.setEnabled(false);
                lineTextArea.setEditable(false);
                lineTextArea.setBorder(null);
                lineTextArea.setBackground(Color.white);
                lineTextArea.setForeground(new Color(153, 153, 153));
                scrollPane3.setViewportView(lineTextArea);
            }
            panel1.add(scrollPane3, "cell 0 0,width 40:40:40");

            //======== scrollPane1 ========
            {

                //---- inputTextPane ----
                inputTextPane.setFont(new Font("Microsoft YaHei", Font.PLAIN, 14));
                inputTextPane.setCursor(Cursor.getPredefinedCursor(Cursor.TEXT_CURSOR));
                inputTextPane.setBorder(null);
                scrollPane1.setViewportView(inputTextPane);
            }
            panel1.add(scrollPane1, "cell 1 0,width 400:400:875,height 640:640:1080");

            //======== scrollPane2 ========
            {

                //---- outputTextPane ----
                outputTextPane.setFont(new Font("Microsoft YaHei", Font.PLAIN, 14));
                outputTextPane.setCursor(Cursor.getPredefinedCursor(Cursor.TEXT_CURSOR));
                outputTextPane.setBorder(null);
                outputTextPane.setSelectionColor(Color.white);
                outputTextPane.setSelectedTextColor(new Color(60, 60, 60));
                outputTextPane.setEditable(false);
                scrollPane2.setViewportView(outputTextPane);
            }
            panel1.add(scrollPane2, "cell 2 0,width 460:460:1005,height 640:640:1080");
        }
        contentPane.add(panel1);
        pack();
        setLocationRelativeTo(getOwner());
        // JFormDesigner - End of component initialization  //GEN-END:initComponents
    }

    // JFormDesigner - Variables declaration - DO NOT MODIFY  //GEN-BEGIN:variables
    private JMenuBar menuBar1;
    private JMenu fileMenu;
    private JMenuItem newEmptyMenuItem;
    private JMenuItem newMenuItem;
    private JMenuItem openMenuItem;
    private JMenuItem saveMenuItem;
    private JMenuItem saveAsMenuItem;
    private JMenu runMenu;
    private JMenuItem LexMenuItem;
    private JMenuItem synMenuItem;
    private JMenuItem semMenuItem;
    private JMenuItem sem2MenuItem;
    private JMenu buildMenu;
    private JMenuItem buildMenuItem;
    private JMenuItem compileMenuItem;
    private JMenuItem uploadMenuItem;
    private JMenu buildMidiMenu;
    private JMenuItem generateMidiMenuItem;
    private JMenuItem playMenuItem;
    private JMenuItem loadSoundFontMenuItem;
    private JMenuItem playDirectMenuItem;
    private JMenuItem stopDirectMenuItem;
    private JMenu helpMenu;
    private JMenuItem InstruMenuItem;
    private JMenuItem TipsMenuItem;
    private JMenuItem demoMenuItem;
    private JMenuItem aboutMenuItem;
    private JPanel hSpacer1;
    private JProgressBar progressBar;
    private JPanel hSpacer2;
    private JPanel panel1;
    private JScrollPane scrollPane3;
    private JTextArea lineTextArea;
    private JScrollPane scrollPane1;
    private JTextPane inputTextPane;
    private JScrollPane scrollPane2;
    private JTextPane outputTextPane;
    // JFormDesigner - End of variables declaration  //GEN-END:variables

}