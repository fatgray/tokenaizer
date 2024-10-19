import java.io.IOException;
import java.util.*;

/**
 * Фабрика по созданию токенайзеров. Создает токенайзер на основе правил.
 * пример использования:
 * <pre>
 * Tokenizer tokenizer = TokenizerFactory.create()
 *                  .addKeyword(new String[] {"begin"})
 *                  .addOperator(new String[] {"/","<","<<=","<>","=","{","}"})
 *                  .addSpaces(new String[] {" \n\r"})
 *                  .addLiteral("#","/")
 *                  .setIgnoreCase(true)
 *                  .setSkipSpace(false)
 *                  .newTokenizer();
 *                  </pre>
 */
public class TokenizerFactory {
    private Alfabet alfabet;
    private StateSet stateSet;

    public HashMap<String,Integer> wordsMap = new HashMap<String,Integer>();
    // Дерево хранения строк ключевых слов
    private CharTreeNode keyWords = new CharTreeNode();
    // Дерево хранения строк окончаний коментариев
    private CharTreeNode endComments = new CharTreeNode();
    // Дерево хранения строк окончания литералов
    private CharTreeNode endLiteral = new CharTreeNode();
    // Дерево хранения строк экранирования литералов
    private CharTreeNode escapeLiteral = new CharTreeNode();
    // Дерево хранения строк пробельных символов
    private CharTreeNode spaceTree = new CharTreeNode();
    private boolean ignoreCase = false;
    private boolean skipSpace=true;


    /**
     * Статический конструктор фабрики
     *
     * @return фабрика
     */
    public static TokenizerFactory create() {
        return new TokenizerFactory();
    }

    /**
     * Добавление правила ключевое слово (keyword).
     * Ключевое слово ищется как подсктрока с полным совпадением
     *
     * @param keywords массив строк
     * @return this эземпляра фабрики
     */
    public TokenizerFactory addKeyword(String[] keywords) {
        for (String str : keywords) {
            addToCharTree(keyWords, str, TokenType.keyword);
        }
        return this;
    }

    /**
     * Добавление правила коментарий.
     * все что входит между начальноым и конечным набором символов считается единым токеном
     * @param begin- стока определяющая начало коментария
     * @param end строка определяющая конец коментария
     * @return this эземпляра фабрики
     */
    public TokenizerFactory addComment(String begin, String end) {
        addToCharTree(keyWords, begin, TokenType.comment);
        addToCharTree(endComments, end, TokenType.comment);
        return this;
    }

    /**
     * Добавление правила для разделителей слов (space).
     * Разделители слов определяют начало и конец токенов, используется для разделения слово по типу пробела
     * набор  последовательных пробельных символов считаем как одна пробельная строка
     *
     * @param spaces строка
     * @return this эземпляра фабрики
     */
    public TokenizerFactory addSpace(String spaces) {
        if (spaces == null) return this;
        for (char ch : spaces.toCharArray()) {
            addToCharTree(spaceTree, Character.toString(ch), TokenType.space);
        }

        return this;
    }

    /**
     * Добавление правила строковый литерал (literal)
     * Строковый литерал - это токен ограниченый подстроками с начала и с конца и с возможностью экранировать
     * конечный ограничитель специальной подстрокой экранирования (escape). Предназначен для поиска токена по типу строковый литерал
     * в языках программирования, например "aaaaa\"ssss"
     *
     * @param border - подстрока определяющая начало и конец литерала
     * @param escape - строка экранирования
     * @return this эземпляра фабрики
     */
    public TokenizerFactory addLiteral(String border, String escape) {

        if (border==null)  return this;
        String escStr = escape + border;
        addToCharTree(keyWords, border, TokenType.literal);
        addToCharTree(endLiteral, border, TokenType.literal);

        if (escape!=null && escape.length()>0) {
            addEscapeToCharTree(endLiteral, escStr, TokenType.literal);
        }

        return this;
    }
    /**
     * В список выходных токенов не выдаем пробельные строки
     * @param skipSpace
     * @return
     */
    public TokenizerFactory setSkipSpace(boolean skipSpace) {
        this.skipSpace=skipSpace;
        return this;
    }
    /**
     * Не учитывать регистр символов
     *
     * @param ignoreCase - учитывать или нет регистр символов
     * @return this эземпляра фабрики
     */
    public TokenizerFactory setIgnoreCase(boolean ignoreCase) {
        this.ignoreCase = ignoreCase;
        return this;
    }

    /**
     * Создание нового экземпляра токенайзера
     *
     * @return токенайзер
     */
    public Tokenizer newTokenizer() {
        prepareAlfabet();
        initialize();

        compileKeyword();
        compileComment();
        compileLiteral();

        TokenizerSetting setting = stateSet.getSetting(alfabet);
        setting.wordsMap=wordsMap;

        Tokenizer tokenizer = new Tokenizer();
        tokenizer.setSetting(setting);
        tokenizer.setSkipSpace(skipSpace);
        return tokenizer;
    }

    private void addEscapeToCharTree(CharTreeNode startNode, String str, TokenType tokenType) {
        CharTreeNode curNode = startNode;
        CharTreeNode node = null;

        for (char ch : str.toCharArray()) {
            node = curNode.get(ch);
            if (node == null) {
                node = curNode.add(ch, tokenType);
                node.isEscape=true;
            }
            curNode = node;
        }
        if (node != null) node.isFinal = true;

    }
    private void addToCharTree(CharTreeNode startNode, String str, TokenType tokenType) {
        CharTreeNode curNode = startNode;
        CharTreeNode node = null;

        for (char ch : str.toCharArray()) {
            node = curNode.get(ch);
            if (node == null) {
                node = curNode.add(ch, tokenType);
            }
            curNode = node;
        }
        if (node != null) {
            node.isFinal = true;
            node.tokenType=tokenType;
            node.tokenText = str;
        }

    }



    private void addCommentChar(CharTreeNode curNode, State curState) {
        CharTreeNode root = endComments;
        char ch = curNode.ch;
        State state = stateSet.newState();
        state.tokenType=TokenType.comment;
        state.link(alfabet, stateSet.commentState);

        curState.link(alfabet.get(curNode.ch), state);

        if (curNode.isFinal) {
            state.link(alfabet, stateSet.stateEndToken);
        }
        for (CharTreeNode node : curNode.getChildren()) {
            addCommentChar(node, state);
        }
    }

    private void addLiteralChar(CharTreeNode curNode, State curState) {

        char ch = curNode.ch;
        State state = stateSet.newState();
        state.link(alfabet, stateSet.literalState);
        state.tokenType=TokenType.literal;

        int abChar = alfabet.get(curNode.ch);
        curState.link(abChar, state);

        if (curNode.isFinal) {
            if (!curNode.isEscape) {
                state.link(alfabet, stateSet.stateEndToken);
            }
            if (curNode.isEscape) {
                state.link(alfabet, stateSet.literalState);
            }
        }

        for (CharTreeNode node : curNode.getChildren()) {
            addLiteralChar(node, state);
        }
    }

    private void compileLiteral() {
        CharTreeNode root = endLiteral;
        for (CharTreeNode node : root.getChildren()) {
            addLiteralChar(node, stateSet.literalState);
        }



    }

    private void compileComment() {

        CharTreeNode root = endComments;
        for (CharTreeNode node : root.getChildren()) {
            addCommentChar(node, stateSet.commentState);
        }

    }

    private void addChar(CharTreeNode curNode, State curState, TokenType tokenType) {
        char ch = curNode.ch;
        State state = stateSet.newState();
        state.tokenType=tokenType;
        curState.link(alfabet.get(curNode.ch), state);
        state.link(alfabet, stateSet.stateAny);
        if (curNode.isFinal) {
            if (curNode.tokenType == TokenType.comment) {
                state.link(alfabet, stateSet.commentState);
            }
            else if (curNode.tokenType == TokenType.literal) {
                state.link(alfabet, stateSet.literalState);
            }
            else if (curNode.tokenType == TokenType.space ) {
                state.link(alfabet, stateSet.spaceState);
            }
            else {
                state.link(alfabet, stateSet.stateEndToken);
                state.tokenType=curNode.tokenType;
                wordsMap.put(curNode.tokenText,state.id);
               // System.out.println(curNode.tokenText+"-"+state.id);
            }
        }

        for (CharTreeNode node : curNode.getChildren()) {
            addChar(node, state,tokenType);
        }
    }

    private void compileKeyword() {
        CharTreeNode root = keyWords;
        for (CharTreeNode node : root.getChildren()) {
            stateSet.stateAny.link(alfabet.get(node.ch), stateSet.wrtBuffer);
            addChar(node, stateSet.wrtBuffer, TokenType.keyword);
        }
    }

    private void prepareAlfabet() {
        alfabet = new Alfabet();

        prepareAlfabet(keyWords);
        prepareAlfabet(endComments);
        prepareAlfabet(endLiteral);
        prepareAlfabet(escapeLiteral);

        CharTreeNode node = spaceTree;
        for (CharTreeNode child : node.getChildren()) {
            alfabet.add(child.ch, alfabet.ab_space);
        }
    }

    public void prepareAlfabet(CharTreeNode node) {

        if (node.ch != 0) alfabet.add(node.ch, ignoreCase);
        for (CharTreeNode child : node.getChildren()) {
            prepareAlfabet(child);
        }

    }

    private void initialize() {
        stateSet = new StateSet(alfabet.length(), alfabet.ab_eos);

        State stateRead = stateSet.newState();

        State stateAny = stateSet.newState();
        stateAny.tokenType=TokenType.word;
        stateAny.link(alfabet, stateRead);
        stateAny.link(alfabet.ab_eos, stateSet.rsFinish);
        stateSet.stateAny = stateAny;

        stateRead.link(alfabet, stateAny);
        stateRead.link(alfabet.ab_eos, stateSet.rsFinish);

        stateSet.wrtBuffer = stateSet.newState();

        State stateEndToken = stateSet.newState();
        State stateBufferAsToken = stateSet.newState();

        stateEndToken.link(alfabet, stateBufferAsToken);
        stateBufferAsToken.link(alfabet, stateAny);

        stateSet.stateEndToken = stateEndToken;
        stateSet.stateBufferAsToken = stateBufferAsToken;

        stateSet.rsStart = stateRead;

        State commentState = stateSet.newState();
        commentState.link(alfabet, commentState);
        commentState.link(alfabet.ab_eos, stateSet.stateEndToken);
        stateSet.commentState = commentState;
        commentState.tokenType=TokenType.comment;

        State literalState = stateSet.newState();
        literalState.tokenType=TokenType.literal;


        State spaceState = stateSet.newState();
        spaceState.tokenType=TokenType.space;
        spaceState.link(alfabet, stateSet.stateEndToken);
        spaceState.link(alfabet.ab_space, spaceState);
        spaceState.link(alfabet.ab_eos, stateSet.stateEndToken);
        stateSet.wrtBuffer.link(alfabet.ab_space, spaceState);
        stateSet.spaceState = spaceState;

        stateSet.stateAny.link(alfabet.ab_space, stateSet.wrtBuffer);


        State literalRead = stateSet.newState();
        literalRead.tokenType=TokenType.literal;
        literalRead.link(alfabet,literalState);

        literalState.link(alfabet, literalRead);
        literalState.link(alfabet.ab_eos, stateSet.stateEndToken);
        stateSet.literalState = literalState;
        stateSet.literalRead=literalRead;
    }

}
/**
 * Класс хранит набор состояний конечного автомата, используется для построения таблицы переходов
 */
class StateSet {
    private ArrayList<State> items = new ArrayList<>();

    private int stateSize;

    public State rsStart;
    public State rsFinish;


    public State commentState;
    public State literalState;
    public State literalRead;
    public State spaceState;

    /////////////////////////////////////
    public State wrtBuffer;
    public State stateEndToken;
    public State stateAny;

    public State stateBufferAsToken;

    public TokenizerSetting getSetting(Alfabet alfabet) {
        TokenizerSetting setting =new TokenizerSetting(alfabet,getTokenTypes(),getChangeState(),null);
        return setting;
    }
    public StateSet(int stateSize, Integer ab_finish) {
        this.stateSize = stateSize;

        rsFinish = newState();
        rsFinish.linkAll(rsFinish);
        rsFinish.link(ab_finish,rsFinish);

    }
    public State newState() {
        Integer newId = items.size();
        State state = new State(newId,stateSize,rsFinish);
        items.add(state);
        return state;
    }
    public int[][] getChangeState() {
        int[][] changeState = new int [items.size()][stateSize];

        for (int i=0;i<items.size();i++) {
            State item = items.get(i);
            int[] itemChangeState = item.getChangeState();
            changeState[i]=itemChangeState;
        }
        return changeState;
    }
    public TokenType[] getTokenTypes() {
        TokenType [] tokenTypes = new TokenType[items.size()];
        for (int i=0;i<items.size();i++) {
            tokenTypes[i]=items.get(i).tokenType;
        }
        return tokenTypes;
    }
}

/**
 * Класс состояние конечного автомата
 */
class State {
    public Integer id;
    public ArrayList<State> changeState;

    public TokenType tokenType=TokenType.empty;

    public State(Integer id, int size, State defState) {
        this.id=id;
        changeState = new ArrayList<>(java.util.Collections.nCopies(size,defState));
    }

    public void link(Integer abChar, State state) {
        changeState.set(abChar,state);
    }
    public void link(Alfabet alfabet, State state) {
        for (int abChar : alfabet.getItems().values()) {
            changeState.set(abChar,state);
        }
        changeState.set(alfabet.ab_alfa,state);
    }

    public int[] getChangeState() {
        int [] result = new int [changeState.size()];
        for (int i=0;i<changeState.size();i++) {
            State item = changeState.get(i);
            result[i]=item.id.byteValue();
        }
        return result;
    }

    public void linkAll(State state) {
        for (int i=1;i<changeState.size();i++) {
            changeState.set(i,state);
        }
    }
}

/**
 * Хранение правил определяемых через пару строк и строку экранирования
 */
class StringPair {
    public String begin;
    public String end;
    public String escape;
    public StringPair(String begin, String end) {
        this.begin=begin;
        this.end=end;
    }
    public StringPair(String begin, String end, String escape) {
        this.begin=begin;
        this.end=end;
        this.escape=escape;
    }
}

/**
 * Класс хранения набора строк в виде дерева символов.
 */
class CharTreeNode  {
    // хранимый символ
    public char ch;
    // полное текст строки для финального символа этой строки, актуально только для ключевого слова
    public String tokenText;
    // список листьев
    public List<CharTreeNode> children;
    // призак что символ является финальным в одной из строк
    public boolean isFinal;
    // признак что символ является символом экранирования
    public boolean isEscape;
    // тип токена для финального символа
    public TokenType tokenType;

    /**
     * Конструктор
     */
    public CharTreeNode() {
        isFinal=false;
        this.ch = 0;
        this.children = new LinkedList<CharTreeNode>();
    }
    /**
     * Добавть дочерний символ
     * @param ch - символ
     * @param tokenType - тип токена
     * @return - новый элемент дерева
     */
    public CharTreeNode add(char ch, TokenType tokenType) {
        CharTreeNode childNode = new CharTreeNode();
        childNode.ch = ch;
        childNode.tokenType=tokenType;
        this.children.add(childNode);
        return childNode;
    }
    /**
     * Получить список дочерних элементов
     * @return
     */
    public  List<CharTreeNode> getChildren() {
        return children;
    }
    /**
     * Получить элемент дерева для заданного символа
     * @param ch - символ для поиска
     * @return элемент дерева или null если не найден
     */
    public CharTreeNode get(char ch) {
        for (CharTreeNode node: children) {
            if (node.ch==ch) return node;
        }
        return null;
    }
}
