package a2u.tn.utils.json;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Parsing JSON string to Map object with JSON5
 * JSON for Humans https://spec.json5.org
 *
 */
@SuppressWarnings("Convert2Diamond")
public class JsonParser {

  /**
   * This name will get element in a returned map when json-string will be an array of values.
   */
  public static final String DEFAULT_LIST_KEY = "list";

  /**
   * Name of root element in a path. Using in call-back methods of IGetCollection.
   * @see IGetCollection#forObject(String)
   * @see IGetCollection#forList(String)
   */
  public static final String PATH_ROOT_KEY = "root";


  /**
   * The callback-interface for specifying type of collection which will be returned when parsing.
   * @see #parse(String, IGetCollection)
   */
  public interface IGetCollection {
    /**
     * This methow will be called when parcer need create new map.<br>
     * If this method returns null, then map will be created with the default type - LinkedHashMap.
     *
     * @param path path of current element, starting from root. If this a root element, then path == "root".
     * @return the empty object who implementing interface from java.util.Map<String, Object>.
     *
     * @see #PATH_ROOT_KEY
     */
    Map<String, Object> forObject(String path);

    /**
     * This methow will be called when parcer need create new array.<br>
     * If this method returns null, then array will be created with the default type - ArrayList.
     *
     * @param path path of current element, starting from root.
     * @return the empty object who implementing interface from java.util.Collection<Object>.
     *
     * @see #PATH_ROOT_KEY
     */
    Collection forList(String path);
  }
  private IGetCollection listener;


  /**
   * Inner immutable class for represent path by root of json-object
   */
  private class Path {
    private String path;

    public Path(String str) {
      this.path = str;
    }

    public String getName() {
      return path;
    }

    public Path add(String node) {
      return new Path(path + '.' + node);
    }

  }

  private static final String NULL = "null";
  private static final String BOOL_TRUE = "true";
  private static final String BOOL_FALSE = "false";
  private static final String NUM_INFINITY = "infinity";
  private static final String NUM_INFINITY_PSITIVE = "+infinity";
  private static final String NUM_INFINITY_NEGATIVE = "-infinity";
  private static final String NUM_NAN = "nan";

  private static final char LF = 0x0A;
  private static final char CR = 0x0D;
  private static final char LS = 0x2028;
  private static final char PS = 0x2029;


  private Map<String, Object> resultMap; // result object
  private String content;                // incoming json-string
  private int maxLength;                 // cache of length the incoming json-string
  private int index;                     // current accepted symbol


  private JsonParser() {
    //hide this
  }



  /**
   * Parsing JSON-string to Map.<br>
   * Every value in the resulting Map will be:
   * <ul>
   *   <li>or a simple value (string or number or boolean),</li>
   *   <li>or a LinkedHashMap with nested json-object,</li>
   *   <li>or an ArrayList of values.</li>
   * </ul>
   *
   * @param data incoming JSON-string.
   * @return Map with data.<br>
   * If JSON contain only array, such as [1,2] then will return Map
   * with single element by key-name DEFAULT_LIST_KEY, which contain list.
   * @see #DEFAULT_LIST_KEY
   *
   */
  public static Map<String, Object> parse(String data) {
    JsonParser p = new JsonParser();
    return p.doParse(data);
  }

  /**
   * Parsing JSON-string to Map with specifying returned collections.<br>
   * For each element representing a non-simple value, will be called the corresponding method of listener,
   * which allows you to set type of the returned object.
   * @see IGetCollection
   *
   * @param data incoming JSON-string.
   * @param listener callback listener.
   * @return Map with data.<br>
   * If JSON contain only array, such as [1,2] then will return Map
   * with single element by key-name DEFAULT_LIST_KEY, which contain list.
   * @see #DEFAULT_LIST_KEY
   */
  public static Map<String, Object> parse(String data, IGetCollection listener) {
    JsonParser p = new JsonParser();
    p.listener = listener;
    return p.doParse(data);
  }


  /**
   * Prepare and start parsing
   * @param data json-string
   * @return java-map object - result of parsing
   */
  private Map<String, Object> doParse(String data) {
    content = data.trim();
    maxLength = content.length();
    index = 0;

    Path emptypath = new Path(PATH_ROOT_KEY);
    resultMap = getCollectionForObject(emptypath);

    parseEmpty(emptypath);

    return resultMap;
  }

  private void parseEmpty(Path emptypath) {
    while (index < maxLength) {
      char c = getTokenBegin();
      if (c == '{') {
        index++;
        resultMap = parseMap(emptypath);
        return;
      }
      else if (c == '[') {
        index++;
        Collection list = parseList(emptypath);
        resultMap.put(DEFAULT_LIST_KEY, list);
      }

      index++;
    }
  }


  private Map<String, Object> parseMap(Path path) {
    Map<String, Object> map = getCollectionForObject(path);

    while (index < maxLength) {
      char c = getTokenBegin(); //skip to begin

      if (c == '}') {
        index++;
        return map;
      }

      String key = extractIdenty();

      c = getTokenBegin();
      if (c != ':') {
        throw new ParseException("Invalid character '" + charToLog(c) + "' at position " + index + " (" + path.getName() + "), expected ':'.", index);
      }
      index++;
      Object val = extractValue(path.add(key));
      map.put(key, val);

      c = getTokenBegin();
      if (c == '}') {
        index++;
        return map;
      }
      if (c == ',') {
        index++;
        continue;
      }

      throw new ParseException("Invalid character '" + charToLog(c) + "' at position " + index + " (" + path.getName() + "), expected ',' or '}'.", index);
    }

    return map;
  }

  private Collection parseList(Path path) {
    Collection list = getCollectionForList(path);

    while (index < maxLength) {
      char c = getTokenBegin();

      if (c == ']') {
        index++;
        return list;
      }

      if (c == ',') {
        index++;
        continue;
      }

      Object val = extractValue(path);
      list.add(val);

    }

    return list;
  }


  /**
   * Return a first symbol which has mean, starting at position = index.
   * Comments will be ignored.
   * index will point to this symbol.
   */
  private char getTokenBegin() {
    while (index < maxLength) {
      char c = content.charAt(index);
      if (Character.isLetterOrDigit(c) || c == '"' || c == '\'' || c == '@' || c == '#' || c == '$' || c == '_' || c == '{' || c == '}' || c == ':' || c == '[' || c == ']' || c == ',' || c == '+' || c == '-' || c == '.') {
        return c;
      }

      if (c == '\\') {
        return c;
      }

      if (c == '/') {
        char next = content.charAt(index + 1);
        if (next == '/') {
          skipToEndLine();
        }
        else if (next == '*') {
          index += 2;
          skipToEndComent();
        }
      }

      index++;
    }
    return 0;
  }

  private void skipToEndLine() {
    while (index < maxLength) {
      char c = content.charAt(index);
      if (isLineTerminator(c)) {
        return;
      }
      index++;
    }
  }

  private void skipToEndComent() {
    while (index < maxLength) {
      char c = content.charAt(index);
      if (c == '*') {
        index++;
        c = content.charAt(index);
        if (c == '/') {
          return;
        }
      }
      index++;
    }
  }



  private String extractIdenty() {
    char c = content.charAt(index);
    char terminator = (c == '"' || c == '\'') ? c : 0;

    StringBuilder b = new StringBuilder();
    while (index < maxLength) {
      c = content.charAt(index);
      if (c == terminator) {
        index++;
        if (b.length() == 0) {
          continue;
        }
        return b.toString().trim();
      }
      if (c == ':' || c == '/') {
        return b.toString().trim();
      }

      if (c == '\\') {
        char ce = getCharFromEscapedText();
        b.append(ce);
      }
      else {
        b.append(c);
      }
      index++;
    }

    return b.toString().trim();
  }

  private Object extractValue(Path path) {
    char c = getTokenBegin();

    if (c == '{') {
      index++;
      Map<String, ?> map = parseMap(path);
      return map;
    }
    if (c == '[') {
      index++;
      Collection list = parseList(path);
      return list;
    }
    if (c == '"' || c == '\'') {
      String str = extractString();
      return str;
    }
    else {
      Object num = extractLiteral();
      return num;
    }

  }


  private Object extractLiteral() {
    StringBuilder b = new StringBuilder();
    while (index < maxLength) {
      char c = content.charAt(index);
      if (c == '+' && b.length() == 0) {
        index++;
      }
      else if (Character.isLetterOrDigit(c) || c == '.' || c == '+' || c == '-') {
        b.append(c);
        index++;
      }
      else {
        break;
      }
    }

    String literal = b.toString().trim().toLowerCase();

    if (literal.equals(NULL)) {
      return null;
    }

    else if (literal.equals(BOOL_TRUE)) {
      return true;
    }
    else if (literal.equals(BOOL_FALSE)) {
      return false;
    }

    else if (literal.equals(NUM_INFINITY)) {
      return Double.POSITIVE_INFINITY;
    }
    else if (literal.equals(NUM_INFINITY_PSITIVE)) {
      return Double.POSITIVE_INFINITY;
    }
    else if (literal.equals(NUM_INFINITY_NEGATIVE)) {
      return Double.NEGATIVE_INFINITY;
    }

    else if (literal.equals(NUM_NAN)) {
      return Double.NaN;
    }

    try {
      return detectNumber(literal);
    }
    catch (Exception ex) {
      throw new ParseException("Invalid literal '" + literal + "' at position " + index + ".", index);
    }

  }

  private Object detectNumber(String literal) {
    boolean hasDot = literal.indexOf('.') >= 0;
    boolean hasE = literal.indexOf('e') >= 0;
    boolean hasX = literal.indexOf('x') >= 0;

    if (hasDot || (hasE && ! hasX)) {
      return Double.parseDouble(literal);
    }

    // Integer.MAX_VALUE dec == 2147483647 - 10 characters
    // Integer.MAX_VALUE hex == 0x7fffffff - 8 characters + 2 (0x) = 10 characters
    // Long.MAX_VALUE dec == 9223372036854775807 - 19 characters
    // Long.MAX_VALUE hex == 0x7fffffffffffffff - 16 characters + 2 (0x) = 18 characters
    //
    // therefore
    // for maxintlen == 9 or 9 characters dec / hex
    // for maxlonglen == 18 or 17 characters dec / hex
    int maxintlen = hasX ? 9 : 9;
    int maxlonglen = hasX ? 17 : 18;
    if (literal.charAt(0) == '-') {
      maxintlen++;
      maxlonglen++;
    }
    if (literal.length() <= maxintlen) {
      return Integer.decode(literal);
    }
    if (literal.length() <= maxlonglen) {
      return Long.decode(literal);
    }
    else {
      return new BigInteger(literal);
    }
  }


  private String extractString() {
    char terminator = content.charAt(index);
    index++;

    StringBuilder b = new StringBuilder();
    while (index < maxLength) {
      char c = content.charAt(index);
      if (c == '\\') {
        char ce = getCharFromEscapedText();
        b.append(ce);
        index++;
      }
      else if (c == terminator) {
        index++;
        break;
      }
      else {
        b.append(c);
        index++;
      }
    }

    return b.toString().trim();
  }

  /**
   * Extract escaped sequences, and convert to char
   * https://spec.json5.org/#escapes
   *
   * As result index will refer to the last accepted character
   *
   * @return matching character
   */
  private char getCharFromEscapedText() {
    char resultChar;
    index++;
    char next = content.charAt(index);
    switch (next) {
      case 'b': resultChar = '\b'; break;
      case 'f': resultChar = '\f'; break;
      case 'n': resultChar = '\n'; break;
      case 'r': resultChar = '\r'; break;
      case 't': resultChar = '\t'; break;
      case '"': resultChar = '"';  break;
      case '\\': resultChar = '\\'; break;
      case 'u':
        StringBuilder bu = new StringBuilder();
        bu.append(content.charAt(++index));
        bu.append(content.charAt(++index));
        bu.append(content.charAt(++index));
        bu.append(content.charAt(++index));
        int hexValU = Integer.parseInt(bu.toString(), 16);
        resultChar = (char) hexValU;
        break;

      case 'x':
      case 'X':
        StringBuilder bx = new StringBuilder();
        char cx = content.charAt(index + 1); //index will refer to the last accepted character, therefore we need to use +1
        while (isHexadecimalChar(cx)) {
          bx.append(cx);
          index++;
          cx = content.charAt(index + 1);
        }
        int hexValX = Integer.parseInt(bx.toString(), 16);
        resultChar = (char) hexValX;
        break;

      default: return next;

    }

    return resultChar;
  }



  /**
   * Line terminator point out to end of single-line comment
   */
  private boolean isLineTerminator(char c) {
    return c == LF || c == CR || c == LS || c == PS;
  }

  private boolean isHexadecimalChar(char c) {
    if (c >= '0' && c <= '9') {
      return true;
    }
    if (c >= 'A' && c <= 'F') {
      return true;
    }
    if (c >= 'a' && c <= 'f') {
      return true;
    }
    return false;
  }

  /**
   * Transform character to string for log
   */
  private String charToLog(char c) {
    switch (c) {
      case '\b': return "\\b";
      case '\f': return "\\f";
      case '\n': return "\\n";
      case '\r': return "\\r";
      case '\t': return "\\t";
      case '\'': return "'";
      case '"': return "\"";
      case LS: return "LS (0x2028)";
      case PS: return "PS (0x2029)";

      case 0: return "null (0x0000)";

      default: return String.valueOf(c);
    }

  }

  private Map<String, Object> getCollectionForObject(Path path) {
    Map<String, Object> result = null;
    if (listener != null) {
      result = listener.forObject(path.getName());
    }
    if (result == null) {
      result = new LinkedHashMap<String, Object>();
    }
    return result;
  }

  private Collection getCollectionForList(Path path) {
    Collection result = null;
    if (listener != null) {
      result = listener.forList(path.getName());
    }
    if (result == null) {
      result = new ArrayList();
    }
    return result;
  }

}
