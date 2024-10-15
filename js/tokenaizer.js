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

var Const = newConst();
var fso = new ActiveXObject("Scripting.FileSystemObject");

function newConst() {
   var obj = new Object();
   obj.RS_READ=1;
   obj.RS_STATE_ALFA =2;
   obj.RS_TOKENSTART =3;
   obj.RS_TOKENEND =4;
   obj.RS_BUFFERASTOKEN =5;
   obj.RS_READ_LITERAL =9;
   obj.RS_STATE_LITERAL =7;
   obj.RS_FINISH=0;
  return obj;
}

function newPosition(line, col) {
    var obj = new Object();
    obj.line=line
    obj.col=1//col

    obj.setPos = function (position) {
        this.line=position.line;
        this.col=position.col;
    }
    obj.setPosXY = function (line, col) {
        this.line=line;
        this.col=col;
    }
   return obj
}

function newToken() {
    var obj = new Object();
    obj.pos = newPosition(0,0);
    obj.state=Const.RS_FINISH;
    obj.text=newStringBuilder();

    obj.isEmpty = function () {
        return this.text.length()==0;
    }
    obj.setStartToken = function (pos) {
        this.pos.setPos(pos);
        this.text.setLength(0);
        this.state=Const.RS_FINISH;
    }
    obj.append = function (ch) {
        this.text.append(ch);
    }
    obj.setState = function (state) {
        this.state=state;
    }
    obj.setToken = function (token) {
        this.pos.setPos(token.pos);
        this.text.append(token.text);
        this.state=token.state;
    }
    obj.addToken = function (token) {
        this.text.append(token.text);
        this.state=token.state;
    }
    return obj;
}
function newAlfabet() {
    var obj = new Object();
    obj.items = new ActiveXObject("Scripting.Dictionary");
    obj.items.CompareMode=0
    obj.ab_dynamic = 0;
    /**
     * Символ конца потока
     */
    obj.ab_eos=null;
    /**
     * Символ "любой другой"
     */
    obj.ab_alfa=null;
    /**
     * Символ рпзделитель слов
     */
    obj.ab_space=null;

    /**
     * Возвращает символы алфавита
     * @return хештаблица символов
     */
    obj.getItems = function () {
        return this.items;
    }

    /**
     * Устанавливает символы алфавита
     * @param items хештаблица символов
     */
    obj.setItems = function (items) {
        this.items=items;
    }


    /**
     * Добавить символ в алфавит
     * @param ch - символ входного алфавита
     * @param abChar - символ внутреннего алфавита
     */
    obj.addAbChar = function (ch, abChar) {
      with (this) {
        if (!items.Exists(ch)) items.Add(ch,abChar)
      }
    }

    /**
     * Добавить массив символов входного алфавита с динамической создания символа внутреннего алфавита
     * @param chars - массив символов входного алфавита
     * @param ignoreCase -
     */
    obj.addCharArray = function (chars, ignoreCase) {
         for (var i=0;i<chars.length;i++) {
            ch=chars[i]
            //WSH.Echo(ch)
            if (ignoreCase) {
                if (!this.items.Exists(ch)) {
                    caseCh = ch.toUpperCase();
                    this.items.Add(caseCh, this.ab_dynamic);
                    var caseChLow = ch.toLowerCase();
                    //WSH.Echo(this.ab_dynamic+" "+ch)
                    this.items.Add(caseChLow, this.ab_dynamic++);
                }
            }
            else {
                if (!this.items.Exists(ch)) {
                    this.items.Add(ch, this.ab_dynamic++);
                }
            }
        }
    }
    obj.addChar= function (ch, ignoreCase) {

            if (ignoreCase) {
                if (!this.items.Exists(ch)) {
                    caseCh = ch.toUpperCase();
                    this.items.Add(caseCh, this.ab_dynamic);
                    caseCh = ch.toLowerCase();
                    this.items.Add(caseCh, this.ab_dynamic++);
                }
            }
            else {
                if (!this.items.Exists(ch)) {
                    this.items.Add(ch, this.ab_dynamic++);
                }
            }
    }

    /**
     * Добавить символы входного алфавита из массива строк во внутренний алфавит
     * @param array - массив строк по которому нужно пробежаться и добавить символы входного алфавита
     * @param ignoreCase
     */
    obj.addAll=function(array, ignoreCase) {
        if (array==null) return;
        for (var i=0;i<array.length;i++) {
            var str =array[i]
	    arr = str.split('')
            this.addCharArray(arr,ignoreCase);
        }
    }

    /**
     * Получить символ внутреннего алфавита по символу входного
     * @param ch - символ входного алфавита
     * @return - символ внутреннего алфавита
     */
    obj.get=function( ch) {
        if (!this.items.Exists(ch)) return 1;
        abChar = this.items.Item(ch);
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
    obj.length=function() {
        return this.ab_dynamic;
    }

    /**
     * Конструктор
     */   
    with (obj) {
        ab_eos = ab_dynamic++;
        ab_alfa= ab_dynamic++;
        ab_space= ab_dynamic++;

        items.Add('\u0000',ab_eos);
        items.Add('\uFFFF',ab_eos);
    }   


    return obj
}


function newFileReader(fileName) {
    var obj = new Object();
  obj.txtFile = fso.OpenTextFile(fileName, 1, false, 0);


  obj.close = function () {
                    this.txtFile.close();
                    this.txtFile=null;
                 }

  obj.read = function () {
        return this.txtFile.read(1);
    }
  return obj
}


function newTokenizerSetting() {
  var obj = new Object()

obj.alfabetaItems = new ActiveXObject("Scripting.Dictionary");
obj.alfabetaItems.Add('\u0000',0);
obj.alfabetaItems.Add('B',3);
obj.alfabetaItems.Add('b',3);
obj.alfabetaItems.Add('D',8);
obj.alfabetaItems.Add('d',8);
obj.alfabetaItems.Add('E',4);
obj.alfabetaItems.Add('e',4);
obj.alfabetaItems.Add('G',5);
obj.alfabetaItems.Add('g',5);
obj.alfabetaItems.Add('\u0027',11);
obj.alfabetaItems.Add('I',6);
obj.alfabetaItems.Add('i',6);
obj.alfabetaItems.Add('*',10);
obj.alfabetaItems.Add('\u000a',12);
obj.alfabetaItems.Add('N',7);
obj.alfabetaItems.Add('n',7);
obj.alfabetaItems.Add('/',9);
obj.alfabetaItems.Add('\uffff',0);

obj.wordsMap = new ActiveXObject("Scripting.Dictionary");
obj.alfabetaItems.Add("end",17);
obj.alfabetaItems.Add("begin",14);

obj.stateTable=[
[  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0],
[  0,  2,  0,  2,  2,  2,  2,  2,  2,  2,  2,  2,  2],
[  0,  1,  3,  3,  3,  1,  1,  1,  1,  3,  1,  3,  1],
[  0,  0,  8, 10, 15,  0,  0,  0,  0, 18,  0, 21,  0],
[  5,  5,  0,  5,  5,  5,  5,  5,  5,  5,  5,  5,  5],
[  2,  2,  0,  2,  2,  2,  2,  2,  2,  2,  2,  2,  2],
[  4,  6,  0,  6,  6,  6,  6,  6,  6,  6, 22,  6, 24],
[  4,  9,  0,  9,  9,  9,  9,  9,  9,  9,  9, 25,  9],
[  4,  4,  8,  4,  4,  4,  4,  4,  4,  4,  4,  4,  4],
[  7,  7,  0,  7,  7,  7,  7,  7,  7,  7,  7,  7,  7],
[  2,  2,  0,  2, 11,  2,  2,  2,  2,  2,  2,  2,  2],
[  2,  2,  0,  2,  2, 12,  2,  2,  2,  2,  2,  2,  2],
[  2,  2,  0,  2,  2,  2, 13,  2,  2,  2,  2,  2,  2],
[  2,  2,  0,  2,  2,  2,  2, 14,  2,  2,  2,  2,  2],
[  4,  4,  0,  4,  4,  4,  4,  4,  4,  4,  4,  4,  4],
[  2,  2,  0,  2,  2,  2,  2, 16,  2,  2,  2,  2,  2],
[  2,  2,  0,  2,  2,  2,  2,  2, 17,  2,  2,  2,  2],
[  4,  4,  0,  4,  4,  4,  4,  4,  4,  4,  4,  4,  4],
[  2,  2,  0,  2,  2,  2,  2,  2,  2, 20, 19,  2,  2],
[  6,  6,  0,  6,  6,  6,  6,  6,  6,  6,  6,  6,  6],
[  6,  6,  0,  6,  6,  6,  6,  6,  6,  6,  6,  6,  6],
[  7,  7,  0,  7,  7,  7,  7,  7,  7,  7,  7,  7,  7],
[  6,  6,  0,  6,  6,  6,  6,  6,  6, 23,  6,  6,  6],
[  4,  4,  0,  4,  4,  4,  4,  4,  4,  4,  4,  4,  4],
[  4,  4,  0,  4,  4,  4,  4,  4,  4,  4,  4,  4,  4],
[  4,  4,  0,  4,  4,  4,  4,  4,  4,  4,  4, 26,  4],
[  7,  7,  0,  7,  7,  7,  7,  7,  7,  7,  7,  7,  7]
];
obj.tokenType=[
"empty",
"empty",
"word",
"empty",
"empty",
"empty",
"comment",
"literal",
"space",
"literal",
"keyword",
"keyword",
"keyword",
"keyword",
"keyword",
"keyword",
"keyword",
"keyword",
"keyword",
"keyword",
"keyword",
"keyword",
"comment",
"comment",
"comment",
"literal",
"literal"
];

obj.wordsMap=null;
  
  return obj;
}

var lCount=0;

function newStringBuilder() {
  var obj = new Object()
  obj.name="stringBuilder"+lCount
  lCount=lCount+1
  obj.text = ""
  obj.setLength = function (newSize) {
     this.text=""
  }
  obj.append = function (str) {
//     WSH.Echo(typeof str)
     this.text = this.text+str
  }
  obj.length = function () {
     return this.text.length;
  }
  obj.toString = function() {
    return this.text
  }
  //WSH.Echo(obj.name)
  return obj
}

 var TokenType = {
     operator : 0,
     literal : 1,
     comment : 2,
     space : 3,
     word : 4,
     empty : 5,
     keyword : 6
}
function createTokenaizer() {
  var obj = new Object()
  obj.state = 0
  obj.ch='\u0000'
  obj.reader = null
  obj.alfabet = null
  obj.stateTable = null
  obj.tokenType = null
  obj.wordsMap  =null;
  obj.tokBuffer=null;
  obj.tokTokenBuffer= newToken();
  obj.abChar=0;
  obj.skipSpace=true
  obj.textPosition= newPosition(0,0);
  obj.bufIndex=1;
    
  //obj12 = new Object()
 // obj.tokenBuffer = newStringBuilder()
  //WSH.Echo(obj.tokenBuffer.name)

  obj.curTokenType = function () {
        return this.tokenType[this.tokTokenBuffer.state];
    }
  obj.setSetting = function (setting) {
        this.stateTable = setting.stateTable;
        this.tokenType = setting.tokenType;
        this.alfabet = newAlfabet();
        this.alfabet.setItems(setting.alfabetaItems);
        this.wordsMap = setting.wordsMap;
  }
  obj.nextAlfabetChar = function ()  {
 	if (this.ch == '\n') {
            this.textPosition.line++;
            this.textPosition.col=0;
        }
        this.ch = this.textReader.read();
        this.textPosition.col++;
        this.abChar = this.alfabet.get(this.ch);
        return this.abChar;
    }
  obj.setReader = function (newReader) {
	this.textReader = newReader;
        this.state=Const.RS_STATE_ALFA;

	this.tokBuffer = newToken();
        this.tokBuffer.state = Const.RS_STATE_ALFA;

        // Прочитаем первый симол потока
        this.textPosition = newPosition(1,1);

        this.ch = this.textReader.read();
        if (this.ch == '\n') {
            this.textPosition.line++;
        }
        this.abChar = this.alfabet.get(this.ch);
        this.tokBuffer.pos.setPosXY(1,1);

	
    }
   /**
     * Вовзращает номер строки для последнего прочитаного токенаю Номера строк начинаются с 0
     * @return номер строки
   */
   obj.curLine = function () {
        return this.tokTokenBuffer.pos.line;
    }
   obj.curPos = function () {
        return this.tokTokenBuffer.pos.col;
    }
   /**
     * Возвращает тип последнего прочитанного токена
     * @return тип токена
    */
    obj.curTokenType = function () {
        return this.tokenType[this.tokTokenBuffer.state];
    }
   /**
    * Чтение очередного токена из потока символов
    * @return строка с текстом токена или null если поток символов закончился
    */
  obj.nextToken = function () {
        var newState=this.state;
        this.tokTokenBuffer.text.setLength(0);
        this.tokTokenBuffer.pos.setPos(this.tokBuffer.pos);
        do {
            switch (this.state) {
                case Const.RS_READ_LITERAL:
                case Const.RS_READ : {
                    this.tokBuffer.append(this.ch);
                    this.abChar = this.nextAlfabetChar();
                    newState = this.stateTable[this.state][this.abChar];
                    this.state = newState;
                    break;
                }
                case Const.RS_STATE_LITERAL:
                case Const.RS_STATE_ALFA: {
                    this.tokBuffer.setState(this.state);
                    newState = this.stateTable[this.state][this.abChar];
                    this.state = newState;
                    break;
                }
                case Const.RS_TOKENEND: {
                    newState = this.stateTable[this.state][this.abChar];
                    this.state = newState;
                    if (!this.tokTokenBuffer.isEmpty()) newState=Const.RS_FINISH;
                    break;
                }
                case Const.RS_TOKENSTART: {
                    this.tokTokenBuffer.addToken(this.tokBuffer);
                    this.tokBuffer.setStartToken(this.textPosition);
                    newState = this.stateTable[this.state][this.abChar];
                    this.tokBuffer.setState(newState);
                    this.state = newState;

                    this.bufIndex=2;
                    break;
                }
                case Const.RS_BUFFERASTOKEN: {
                    this.tokTokenBuffer.setToken(this.tokBuffer);
                    this.tokBuffer.setStartToken(this.textPosition);
                    newState = this.stateTable[this.state][this.abChar];
                    this.state = newState;
                    newState=Const.RS_FINISH;
                    this.bufIndex=2;
                    break;
                }
                case Const.RS_FINISH: {
                    break;
                }
                default : {
                    if (this.abChar!=this.alfabet.ab_eos) {
                        this.tokBuffer.append(this.ch);
                    }
                    this.tokBuffer.setState(this.state);
                    this.abChar= this.nextAlfabetChar();
                    newState = this.stateTable[this.state][this.abChar];
                    this.state = newState;
                }
            }
        }
        while (newState>0);

        if (this.bufIndex==1) {
            this.tokTokenBuffer.setToken(this.tokBuffer);
            this.tokBuffer.setStartToken(this.textPosition);
        }

        if (this.tokTokenBuffer.isEmpty())  {
            this.tokTokenBuffer.state=Const.RS_FINISH;
            return null;
        }
        // если настроено пропускать пробельные токены
        if (this.skipSpace && this.curTokenType()==TokenType.space) {
            return this.nextToken();
        }
        else {
            this.bufIndex=1;
            return this.tokTokenBuffer.text.toString();
        }
  }  

  obj.openFile = function (fileName) {
      reader = newFileReader(fileName);
      this.setReader(reader);
  }
  obj.close = function() {
     this.reader.close();
  }

  setting = newTokenizerSetting();
  obj.setSetting(setting)

  return obj;
}





tokenaizer = createTokenaizer();
tokenaizer.openFile("d:\\test.txt");

var token=tokenaizer.nextToken();
while (token!==null) {
  WSH.Echo(tokenaizer.curTokenType()+" token='"+ token+"'" + " line="+tokenaizer.curLine()+":"+tokenaizer.curPos()+" ");
  token=tokenaizer.nextToken();
}
  
tokenaizer.close();

                                                                    
