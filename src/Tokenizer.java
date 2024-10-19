/*      Tokenaizer library for split text on tokens by rule
        Copyright (C) 2024  Fatiev Michail

        This program is free software; you can redistribute it and/or
        modify it under the terms of the GNU General Public License
        as published by the Free Software Foundation; either version 2
        of the License, or (at your option) any later version.

        This program is distributed in the hope that it will be useful,
        but WITHOUT ANY WARRANTY; without even the implied warranty of
        MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
        GNU General Public License for more details.

        You should have received a copy of the GNU General Public License
        along with this program; if not, see
        <https://www.gnu.org/licenses/>.

        Fatiev Michail fatgraynex@gmail.com
*/

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.*;
import java.util.*;

/**
 *
 * Класс рабора потока символов на токены по заданным правилам. Описани правил можно посмотреть в описании TokenizerFactory
 * смотрит вперед по тексту для разрешения коллизий.
 * <p>
 * Правила задаются в скомпилированном виде или загружаются из json или через фабрику тоенайзеров
 * </br><pre>  TokenizerFactory.create().addKeyword(new String[] {"begin"})
 *                            .addKeyword(new String[] {":=","::"})
 *                            .setIgnoreCase(true)
 *                            .newTokenizer();
 *                            </pre>
 * </br>Описание правил: <li>keywords - ключевые слова - ищется в тексте полное совпадение со списком </li>
 *                   <li>operators - операторы - ищется в тексте полное совпадение со списком </li>
 *                   <li>literal - литералы - ищется фраза ограниченная слева и справа, пропускаЯ экранированные справа </li>
 *                   <li>comment - коментарий - ищется фраза ограниченная справа и слева или однострочный коментарий </li>
 *                   <li>space - разделители слов -  ищется подряд идущие символы разделители </li>
 *                   <li>word - слова - текст между разделителями слов или другими выражениями </li>
 *                   <li>empty - пустой токен, выдается в конце поиска <li/>
 *                   </p>
 * </p>Пример использования: <pre>
 *    tokenizer.openFile("d:\\test.txt");
 *    String token;
 *    do {
 *        token = tokenizer.nextToken();
 *        System.out.println(tokenizer.curTokenType()+":" + token + " line="+tokenizer.curLine());
 *    } while (token!=null);
 *    reader.close();
 * </pre>
 *  @see TokenizerFactory
 */
public class Tokenizer {
    private HashMap<String,Integer> wordsMap;
    // таблица переходов состояний
    private int [][] stateTable;
    // очередь типов прочитанных токенов
    private TokenType [] tokenType ;
    // Алфавит вычисление формируется из входного при задании правил
    private Alfabet alfabet;
    Token tokBuffer = new Token();
    Token tokTokenBuffer = new Token();
    // текущее состояние
    private int state;
    // текущий символ исходного алфавита
    char ch=0;
    // текущий символ алфавита
    int abChar =0;
    // выдавать на выход разделители токенов
    private boolean skipSpace=true;
    // читатель символов
    private Reader textReader;
    // позиция текущего символа  в потоке
    private Position textPosition = new Position(1,1);
    /**
     * возвращать/невозвращать токены разделители слов
     * @param skipSpace логический тип
     * @return токенайзер
     */
    public Tokenizer setSkipSpace(boolean skipSpace) {
        this.skipSpace = skipSpace;
        return this;
    }
    /**
     * Конструктор
     */
    Tokenizer() {

    }
    /**
     * Установка скомпилированных правил
     * @param setting
     */
    public void setSetting(TokenizerSetting setting) {
        stateTable = setting.stateTable;
        tokenType = setting.tokenType;
        this.alfabet = new Alfabet();
        this.alfabet.setItems(setting.alfabetaItems);
        this.wordsMap = setting.wordsMap;
    }

    /**
     * Возвращает настройки правил
     * @return TokenizerSetting - настройки
     */
    public TokenizerSetting getSetting() {
        return  new TokenizerSetting(alfabet,tokenType,  stateTable,wordsMap);
    }

    /**
     * Открытие потока данных для парсинга из текстового файла
     * @param fileName
     * @throws IOException
     */
    public void openFile(String fileName) throws IOException {
        Reader reader = new FileReader(fileName);
        setReader(reader);
    }
    /**
     *  Открытие потока  данных для парсинга из строки
     * @param text
     * @throws IOException
     */
    public void openString(String text) throws IOException {
        Reader reader = new  StringReader(text);
        setReader(reader);
    }
    /**
     *  Установка потока для парсинга
     * @param reader - входной поток символов
     * @throws IOException
     */
    public void setReader(Reader reader) throws IOException {
        this.textReader = reader;
        state=Const.RS_STATE_ALFA;

        tokBuffer = new Token();
        tokBuffer.state = Const.RS_STATE_ALFA;

        // Прочитаем первый симол потока
        textPosition = new Position(1,1);
        ch = (char)textReader.read();
        if (ch == '\n') {
            textPosition.line++;
        }
        abChar = alfabet.get(ch);
        tokBuffer.pos.setPosXY(1,1);
    }

    public HashMap<String, Integer> getWordsMap() {
        return wordsMap;
    }
    public void close() throws IOException {
        textReader.close();
    }
    // текущее количество используемых буферов под токены. два значит заглянули вперед
    private int bufIndex=1;
    /**
     * Чтение очередного токена из потока символов
     * @return строка с текстом токена или null если поток символов закончился
     * @throws IOException
     */
    public  String nextToken() throws IOException {
        int newState=state;
        tokTokenBuffer.text.setLength(0);
            tokTokenBuffer.pos.setPos(tokBuffer.pos);
        do {
            switch (this.state) {
                case Const.RS_READ_LITERAL:
                case Const.RS_READ : {
                    tokBuffer.append(ch);
                    abChar = nextAlfabetChar();
                    newState = stateTable[state][abChar];
                    state = newState;
                    break;
                }
                case Const.RS_STATE_LITERAL:
                case Const.RS_STATE_ALFA: {
                    tokBuffer.setState(state);
                    newState = stateTable[state][abChar];
                    state = newState;
                    break;
                }
                case Const.RS_TOKENEND: {
                    newState = stateTable[state][abChar];
                    state = newState;
                    if (!tokTokenBuffer.isEmpty()) newState=Const.RS_FINISH;
                    break;
                }
                case Const.RS_TOKENSTART: {
                    tokTokenBuffer.addToken(tokBuffer);

                    tokBuffer.setStartToken(textPosition);
                    newState = stateTable[state][abChar];
                    tokBuffer.setState(newState);
                    state = newState;

                    bufIndex=2;
                    break;
                }
                case Const.RS_BUFFERASTOKEN: {
                    tokTokenBuffer.setToken(tokBuffer);
                    tokBuffer.setStartToken(textPosition);
                    newState = stateTable[state][abChar];
                    state = newState;
                    newState=Const.RS_FINISH;
                    bufIndex=2;
                    break;
                }
                case Const.RS_FINISH: {
                    break;
                }
                default : {
                    if (abChar!=alfabet.ab_eos) {
                        tokBuffer.append(ch);
                    }
                    tokBuffer.setState(state);
                    abChar= nextAlfabetChar();
                    newState = stateTable[state][abChar];
                    state = newState;
                }
            }
        }
        while (newState>0);

        if (bufIndex==1) {
            tokTokenBuffer.setToken(tokBuffer);
            tokBuffer.setStartToken(textPosition);
        }

        if (tokTokenBuffer.isEmpty())  {
            tokTokenBuffer.state=Const.RS_FINISH;
            return null;
        }
        // если настроено пропускать пробельные токены
        if (skipSpace && curTokenType()==TokenType.space) {
            return nextToken();
        }
        else {
            bufIndex=1;
            return tokTokenBuffer.text.toString();
        }
    }
    /**
     * Возвращает строку текущего токена
     * @return токен
     */
    public String curToken() {
        return tokTokenBuffer.toString();
    }
    /**
     * Возвращает тип последнего прочитанного токена
     * @return тип токена
     */
    public TokenType curTokenType() {
        return tokenType[tokTokenBuffer.state];
    }
    /**
     * Вовзращает номер строки для последнего прочитаного токенаю Номера строк начинаются с 0
     * @return номер строки
     */
    public int curLine() {
        return tokTokenBuffer.pos.line;
    }
    /**
     * Вовзращает позцию в cтроке  для последнего прочитаного токенаю
     * @return позция в строке
     */
    public int curPos() {
        return tokTokenBuffer.pos.col;
    }
    /**
     * Вовзращает id токена
     * @return id типа токена
     */
    public int curTokenId() {
        return tokTokenBuffer.state;
    }
    /**
     * получить текущий символ алфавита
     * @return символ алфавита
     * @throws IOException
     */
    public int nextAlfabetChar() throws IOException {
        if (ch == '\n') {
            textPosition.line++;
            textPosition.col=0;
        }
        ch = (char)textReader.read();
        textPosition.col++;
        abChar = alfabet.get(ch);
        return abChar;
    }
}
class Position {
    int line;
    int col;

    Position(int line, int col) {
        this.line = line;
        this.col = col;
    }
    public void setPos(Position position) {
        this.line=position.line;
        this.col=position.col;
    }
    public void setPosXY(int line, int col) {
        this.line=line;
        this.col=col;
    }
}

class Token {
    Position pos;
    int state;
    StringBuilder text;

    Token() {
        pos = new Position(0,0);
        state=Const.RS_FINISH;
        text = new StringBuilder();
    }
    public boolean isEmpty() {
        return text.length()==0;
    }
    public void setStartToken(Position pos) {
        this.pos.setPos(pos);
        text.setLength(0);
        state=Const.RS_FINISH;
    }
    public void append(char ch) {
        text.append(ch);
    }
    public void setState(int state) {
        this.state=state;
    }
    public void setToken(Token token) {
        pos.setPos(token.pos);
        text.append(token.text);
        state=token.state;
    }
    public void addToken(Token token) {
        text.append(token.text);
        state=token.state;
    }
}
/**
 * Класс алфавит. Используется для перехода из обычного алфавита во внутренний алфавит алгоритма.
 * Новый алфавит формируется динамически. Класс нужен для оптимизации.
 */
class Alfabet {
    private HashMap<Character,Integer> items = new HashMap<>();
    private Integer ab_dynamic = 0;
    /**
     * Символ конца потока
     */
    public final Integer ab_eos;
    /**
     * Символ "любой другой"
     */
    public final Integer ab_alfa;
    /**
     * Символ рпзделитель слов
     */
    public  Integer ab_space;

    /**
     * Возвращает символы алфавита
     * @return хештаблица символов
     */
    public HashMap<Character,Integer> getItems() {
        return items;
    }

    /**
     * Устанавливает символы алфавита
     * @param items хештаблица символов
     */
    public void setItems(HashMap<Character,Integer> items) {
        this.items=items;
    }

    /**
     * Конструктор
     */
    public Alfabet() {
        ab_eos = ab_dynamic++;
        ab_alfa= ab_dynamic++;
        ab_space= ab_dynamic++;

        items.put('\u0000',ab_eos);
        items.put('\uFFFF',ab_eos);
    }

    /**
     * Добавить символ в алфавит
     * @param ch - символ входного алфавита
     * @param abChar - символ внутреннего алфавита
     */
    public void add(char ch, Integer abChar) {
        items.put(ch,abChar);
    }

    /**
     * Добавить массив символов входного алфавита с динамической создания символа внутреннего алфавита
     * @param chars - массив символов входного алфавита
     * @param ignoreCase -
     */
    public void add(char[] chars, boolean ignoreCase) {
        for (char ch : chars) {
            if (ignoreCase) {
                if (!this.items.containsKey(ch)) {
                    char caseCh = Character.toUpperCase(ch);
                    this.items.put(caseCh, ab_dynamic);
                    caseCh = Character.toLowerCase(ch);
                    this.items.put(caseCh, ab_dynamic++);
                }
            }
            else {
                if (!this.items.containsKey(ch)) {
                    this.items.put(ch, ab_dynamic++);
                }
            }
        }
    }
    public void add(char ch, boolean ignoreCase) {

            if (ignoreCase) {
                if (!this.items.containsKey(ch)) {
                    char caseCh = Character.toUpperCase(ch);
                    this.items.put(caseCh, ab_dynamic);
                    caseCh = Character.toLowerCase(ch);
                    this.items.put(caseCh, ab_dynamic++);
                }
            }
            else {
                if (!this.items.containsKey(ch)) {
                    this.items.put(ch, ab_dynamic++);
                }
            }
    }

    /**
     * Добавить символы входного алфавита из массива строк во внутренний алфавит
     * @param array - массив строк по которому нужно пробежаться и добавить символы входного алфавита
     * @param ignoreCase
     */
    public void addAll(String [] array, boolean ignoreCase) {
        if (array==null) return;
        for (String str: array) {
            add(str.toCharArray(),ignoreCase);
        }
    }

    /**
     * Получить символ внутреннего алфавита по символу входного
     * @param ch - символ входного алфавита
     * @return - символ внутреннего алфавита
     */
    public int get(char ch) {
        Integer abChar = items.get(ch);
        if (abChar==null) {
            return ab_alfa;
        }
        else {
            return abChar;
        }
    }

    /**
     * Размер внутреннего алфавита
     * @return размер
     */
    public int length() {
        return ab_dynamic;
    }
}

/**
 * Общие константы
 */
class Const {
    public static final byte RS_READ=1;
    public static final byte RS_STATE_ALFA =2;
    public static final byte RS_TOKENSTART =3;
    public static final byte RS_TOKENEND =4;
    public static final byte RS_BUFFERASTOKEN =5;
    public static final byte RS_READ_LITERAL =9;
    public static final byte RS_STATE_LITERAL =7;
    public static final byte RS_FINISH=0;
}

/**
 * Типы токенов:
 *    <li>keywords - ключевые слова - ищется в тексте полное совпадение со списком </li>
 *    <li>operators - операторы - ищется в тексте полное совпадение со списком </li>
 *    <li>literal - литералы - ищется фраза ограниченная слева и справа, пропускаЯ экранированные справа </li>
 *    <li>comment - коментарий - ищется фраза ограниченная справа и слева или однострочный коментарий </li>
 *    <li>space - разделители слов -  ищется подряд идущие символы разделители </li>
 *    <li>word - слова - текст между разделителями слов или другими выражениями </li>
 */
 enum TokenType {
     operator,
     literal,
     comment,
     space,
     word,
     empty,
     keyword
}

/**
 * Настройке токенайзера в компилированном виде.
 */
class TokenizerSetting {
    public HashMap<String,Integer> wordsMap;
    HashMap<Character,Integer> alfabetaItems;
    TokenType[]  tokenType;
    int [][] stateTable;

    public TokenizerSetting() {

    }
    public TokenizerSetting(Alfabet alfabeta, TokenType[] tokenType, int [][] stateChange, HashMap<String,Integer>  wordsMap) {
        this.alfabetaItems =alfabeta.getItems();
        this.tokenType=tokenType;
        this.stateTable =stateChange;
        this.wordsMap=wordsMap;
    }
    /**
     * Запись скомпилированных правил разбора в файл формата json
     * @param fileName имя файла
     * @throws IOException
     */
    public void save(String fileName) throws IOException {
        Gson gson = new GsonBuilder()
                .setPrettyPrinting()
                .create();
        FileWriter writer = new FileWriter(fileName);
        writer.write(gson.toJson(this));
        writer.close();
    }
    public void saveJavaScript(String fileName) throws IOException {
        FileWriter writer = new FileWriter(fileName);

        ArrayList<Character> keys = new ArrayList<>(alfabetaItems.keySet());
        ArrayList<Integer> ValItems = new ArrayList(alfabetaItems.values());

        writer.write("alfabetaItems = new ActiveXObject(\"Scripting.Dictionary\");\n");
        writer.write(String.format("alfabetaItems.Add('\\u%04x',%d);\n",(int) keys.get(0),ValItems.get(0)));
        for (int i=1;i< keys.size();i++) {
            Character ch= keys.get(i);
            if ((Character.isLetterOrDigit(ch) ||
                    Character.getType(ch)==Character.OTHER_PUNCTUATION ||
                    Character.getType(ch)==Character.START_PUNCTUATION ||
                    Character.getType(ch)==Character.END_PUNCTUATION
            )
                    && !ch.equals('\'')
            )
            {
                writer.write(String.format("alfabetaItems.Add('%c',%d);\n",ch,ValItems.get(i)));
            }
            else{
                writer.write(String.format("alfabetaItems.Add('\\u%04x',%d);\n",(int)ch,ValItems.get(i)));
            }
        }
        writer.write("\n");

        ArrayList<String> wordsKey = new ArrayList<>(wordsMap.keySet());
        ValItems = new ArrayList(wordsMap.values());

        writer.write("wordsMap = new ActiveXObject(\"Scripting.Dictionary\");\n");

        for (int i=0;i< wordsKey.size();i++) {
            writer.write(String.format("alfabetaItems.Add(\"%s\",%d);\n",wordsKey.get(i),ValItems.get(i)));
        }
        writer.write("\n");

        writer.write("stateTable=[\n");
        for (int i=0;i<stateTable.length;i++) {
            writer.write("[");
            for (int j=0;j<stateTable[i].length;j++) {
                writer.write(String.format("%3d",stateTable[i][j]));
                if (j<stateTable[i].length-1) {
                    writer.write(",");
                }
            }
            writer.write("]");
            if (i<stateTable.length-1) {
                writer.write(",\n");
            }
        }
        writer.write("\n];\n");
        writer.write("tokenType=[\n");
        for (int i=0;i<tokenType.length;i++) {
            writer.write(String.format("\"%s\"",tokenType[i]));
            if (i<tokenType.length-1) {
                writer.write(",");
            }
            writer.write("\n");
        }
        writer.write("];\n");
        writer.close();
    }
    /**
     * Чтение скомпилированных правил разбора из json файла через ридер
     * @param reader имя файла
     * @throws IOException
     */
    public static TokenizerSetting load(Reader reader) throws IOException {
        Gson gson = new GsonBuilder()
                .setPrettyPrinting()
                .create();
        TokenizerSetting setting = gson.fromJson(reader, TokenizerSetting.class);
        return setting;
    }
    /**
     * Чтение скомпилированных правил разбора из json файла
     * @param fileName имя файла
     * @throws IOException
     */
    public static TokenizerSetting load(String fileName) throws IOException {
        FileReader reader = new FileReader(fileName);
        return load(reader);
    }

}





