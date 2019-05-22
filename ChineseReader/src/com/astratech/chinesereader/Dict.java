package com.astratech.chinesereader;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.util.Log;

import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.Collections;

/**
 * Created by lastant on 12/3/13.
 */
public class Dict {
  public static IntBuffer entries;
  public static RandomAccessFile dictFile;
  public static byte[] byteBuffer;
  public static ArrayList<byte[]> dictParts;
  public static ArrayList<Integer> dictIndexes;
  public static SharedPreferences sharedPrefs;

  public static void loadDict (Context context) {

    try {
      boolean resaveEntries = false;
      dictParts = new ArrayList<byte[]>();
      dictIndexes = new ArrayList<Integer>();

      File dictFd = new File(context.getFilesDir(),  "dict.db");
      if (!dictFd.exists()) {// || dictFd.length() != 4961308) {
        copyFile(context, "dict.db");
        dictFd = new File(context.getFilesDir(), "dict.db");
        resaveEntries = true;
      }
      dictFile = new RandomAccessFile(dictFd, "r");

      File idxFd = new File(context.getFilesDir(),  "idx.db");
      if (!idxFd.exists()) {// || idxFd.length() != 3145553) {
        copyFile(context, "idx.db");
        idxFd = new File(context.getFilesDir(), "idx.db");
        resaveEntries = true;
      }
      FileInputStream idxBuf = new FileInputStream(idxFd);

      if (!new File(context.getFilesDir(),  "entries.bin").exists() || !new File(context.getFilesDir(),  "parts.bin").exists()) {
        resaveEntries = true;
      }

      entries = IntBuffer.allocate(1649830);

      int index = 0;

      if (idxBuf != null) {
        int readLen, offset = 0, partLen = 200000;
        byte[] dictPart = new byte[partLen];
        int totalRead = 0;
        int totalLen = (int)idxFd.length();
        while (totalRead < totalLen && (readLen = idxBuf.read(dictPart, offset, dictPart.length - offset)) > 0) {
          totalRead += readLen;
          int j = offset + readLen - 1;

          byte[] newDictPart = null;

          if (readLen == partLen - offset) {
            while (dictPart[j] >= 0) j--;
            while (dictPart[j] < 0) j--;
            offset = partLen - j - 1;
            newDictPart = new byte[(int)Math.min(totalLen - totalRead + offset, partLen)];
            System.arraycopy(dictPart, j + 1, newDictPart, 0, offset);
          } else {
            offset = 0;
          }

          if (resaveEntries) {
            dictIndexes.add(index);

            int i = 0;
            while (i <= j) {
              entries.put(index++, i);

              while (i <= j && dictPart[i] < 0) {
                i++;
              }
              while (i <= j && dictPart[i] >= 0) {
                i++;
              }
            }
          }

          dictParts.add(dictPart);
          dictPart = newDictPart;
        }
        idxBuf.close();
      }

      if (resaveEntries) {
        DataOutputStream entriesOut = null, partsOut = null;

        entriesOut = new DataOutputStream(context.openFileOutput("entries.bin", Context.MODE_PRIVATE));
        int count = entries.capacity();
        for (int i = 0; i < count; i++) {
          entriesOut.writeInt(entries.get(i));
        }

        partsOut = new DataOutputStream(context.openFileOutput("parts.bin", Context.MODE_PRIVATE));
        for (int i : dictIndexes) {
          partsOut.writeInt(i);
        }

        if (entriesOut != null) {
          entriesOut.flush();
          entriesOut.close();
        }
        if (partsOut != null) {
          partsOut.flush();
          partsOut.close();
        }
      } else {
        FileInputStream entriesIn = null, partsIn = null;

        entriesIn = context.openFileInput("entries.bin");
        FileChannel file = entriesIn.getChannel();
        ByteBuffer bb = ByteBuffer.allocate(4 * 1649830);
        file.read(bb);
        bb.rewind();
        entries = bb.asIntBuffer();
        file.close();

        partsIn = context.openFileInput("parts.bin");
        file = partsIn.getChannel();
        bb = ByteBuffer.allocate((int)file.size());
        file.read(bb);
        bb.rewind();
        IntBuffer ib = bb.asIntBuffer();
        int count = ib.capacity();
        for (int i = 0; i < count; i++) {
          dictIndexes.add(ib.get(i));
        }
        file.close();

        if (entriesIn != null) {
          entriesIn.close();
        }
        if (partsIn != null) {
          partsIn.close();
        }
      }

    } catch (Exception e) {
      Log.e("chinesereader", e.getMessage());
    }

    byteBuffer = new byte[1090];

    sharedPrefs = PreferenceManager.getDefaultSharedPreferences(context);
  }

  private static void copyFile(Context context, String fileName) {
    try {
      OutputStream os = context.openFileOutput(fileName, Context.MODE_PRIVATE);
      InputStream is = context.getAssets().open(fileName);

      AnnotationActivity.copyFile(is, os);

      os.flush();
      os.close();
      is.close();

    } catch (Exception e) { }
  }

  public static byte[] getDictPart(int entry) {
    int part = Collections.binarySearch(dictIndexes, entry);
    if (part < 0 ) {
      part = -part - 2;
    }

    return dictParts.get(part);
  }

  public static int compare(int entry, String another, boolean broad) {
    int i = entries.get(entry), j = 0, len = another.length();
    byte[] dict = getDictPart(entry);

    char c1 = (char)(((dict[i] & 0x0F) << 12) | ((dict[i + 1] & 0x3F) << 6) | (dict[i + 2] & 0x3F));
    char c2 = 0;

    while (j < len && dict[i] < 0) {
      c2 = another.charAt(j);
      if (c1 == c2) {
        i += 3;
        c1 = (char)(((dict[i] & 0x0F) << 12) | ((dict[i + 1] & 0x3F) << 6) | (dict[i + 2] & 0x3F));
        j++;
      } else {
        break;
      }
    }

    if (dict[i] >= 0) {
      if (j == len) {
        return 0;
      } else {
        return -1;
      }
    } else {
      if (j == len) {
        if (broad) {
          return 0;
        } else {
          return 1;
        }
      } else {
        if (c1 > c2) {
          return 1;
        } else {
          return -1;
        }
      }
    }
  }

  public static String getCh(int entry, String charType) {
    try{
      byte[] dict = getDictPart(entry);

      if (charType.equals("original")) {
        int i = entries.get(entry);
        while (dict[i++] < 0);
        return new String(dict, entries.get(entry), i - entries.get(entry) - 1, "UTF-8");
      } else if (charType.equals("simplified")) {
        int i = entries.get(entry);

        while (dict[i] < 0) { i++; }

        if (dict[i++] == 0) {
          int index = 0, mult = 1;
          while (i < dict.length && dict[i] >= 0) {
            index += mult * dict[i++];
            mult *= 128;
          }
          i = entries.get(index);
          dict = getDictPart(index);
          while (dict[i++] < 0);

          return new String(dict, entries.get(index), i - entries.get(index) - 1, "UTF-8");
        }

        return new String(dict, entries.get(entry), i - entries.get(entry) - 1, "UTF-8");
      } else if (charType.equals("traditional")) {
        int i = entries.get(entry);

        while (dict[i] < 0) { i++; }

        if (dict[i++] == 0) {
          return new String(dict, entries.get(entry), i - entries.get(entry), "UTF-8");
        } else {
          while (dict[i++] != 0);
          int index = 0, mult = 1;
          while (i < dict.length && dict[i] >= 0) {
            index += mult * dict[i++];
            mult *= 128;
          }

          try {
            dictFile.seek(index);
            byte ch;
            int j = 0;
            while ((ch = dictFile.readByte()) < 0) {
              byteBuffer[j++] = ch;
            }
            return new String(byteBuffer, 0, j, "UTF-8");
          } catch (Exception e) {
          }

        }
      }

      return "";
    } catch (Exception e) {
      return "";
    }
  }

  public static String getCh(int entry) {
    String charType = sharedPrefs.getString("pref_charType", "original");

    return getCh(entry, charType);
  }

  public static boolean equals(int entry, String str) {
    int len = str.length();
    boolean found = true;
    byte[] dict = getDictPart(entry);

    for (int i = entries.get(entry), j = 0; dict[i] < 0; i += 3, j++) {
      if (j > len || str.charAt(j) != (char)(((dict[i] & 0x0F) << 12) | ((dict[i + 1] & 0x3F) << 6) | (dict[i + 2] & 0x3F))) {
        found = false;
        break;
      }
    }

    return found;
  }

  public static int getLength(int entry) {
    int i = entries.get(entry);
    byte[] dict = getDictPart(entry);
    while (dict[i++] < 0);
    return (i - entries.get(entry) - 1) / 3;
  }

  public static String getPinyin(int entry) {
    int i = entries.get(entry);
    byte[] dict = getDictPart(entry);

    while (dict[i] < 0) { i++; }

    if (dict[i] == 0) {
      i++;
      int index = 0, mult = 1;
      while (i < dict.length && dict[i] >= 0) {
        index += mult * dict[i++];
        mult *= 128;
      }
      i = entries.get(index);
      dict = getDictPart(index);
      while (dict[i] < 0) { i++; }
    }

    int j = i;
    while (dict[j] != 0) { j++; }

    try {
      return new String(dict, i, j - i, "US-ASCII");
    } catch(Exception e) {
      return "";
    }
  }

  public static String getEnglish (int entry){
    int i = entries.get(entry);
    byte[] dict = getDictPart(entry);

    while (dict[i] < 0) { i++; }

    if (dict[i++] == 0) { //traditional link
      int index = 0, mult = 1;
      while (i < dict.length && dict[i] >= 0) {
        index += mult * dict[i++];
        mult *= 128;
      }
      i = entries.get(index);
      dict = getDictPart(index);
      while (dict[i] < 0) { i++; }
    }

    while (dict[i++] != 0);

    int index = 0, mult = 1;
    while (i < dict.length && dict[i] >= 0) {
      index += mult * dict[i++];
      mult *= 128;
    }

    try {
      dictFile.seek(index);
      byte ch;
      int j = 0;
      while ((ch = dictFile.readByte()) < 0);

      byteBuffer[j++] = ch;
      while ((ch = dictFile.readByte()) != '\n') {
        byteBuffer[j++] = ch;
      }
      return new String(byteBuffer, 0, j, "UTF-8");
    } catch (Exception e) {
    }

    return "";
  }

  public static int binarySearch(String key, int start, int end, boolean broad) {
    int lo = start;
    int hi = end;
    while (lo <= hi) {
      int mid = lo + (hi - lo) / 2;
      int res = compare(mid, key, broad);
      if      (res > 0) hi = mid - 1;
      else if (res < 0) lo = mid + 1;
      else return mid;
    }
    return -1;
  }

  public static int binarySearch(String key, boolean broad) {
    return binarySearch(key, 0, entries.capacity() - 1, broad);
  }


  public static String pinyinToTones(String py) {
    String pinyinType = sharedPrefs.getString("pref_pinyinType", "marks");
    if (pinyinType.equals("numbers")) {
      return py;
    }

    String[] parts = py.split("(?<=[1-5])");
    int len = parts.length;
    StringBuilder pinyin = new StringBuilder();
    for (int j = 0; j < len; j++) {
      pinyin.append(convertToneNumber2ToneMark(parts[j].trim()));
    }

    return pinyin.toString();
  }

  private static String convertToneNumber2ToneMark(final String pinyinStr) {

    final char defaultCharValue = '$';
    final int defaultIndexValue = -1;

    char unmarkedVowel = defaultCharValue;
    int indexOfUnmarkedVowel = defaultIndexValue;

    final char charA = 'a';
    final char charE = 'e';
    final String ouStr = "ou";
    final String allUnmarkedVowelStr = "aeiouv";
    final String allMarkedVowelStr = "\u0101\u00E1\u0103\u00E0a\u0113\u00E9\u0115\u00E8e\u012B\u00ED\u012D\u00ECi\u014D\u00F3\u014F\u00F2o\u016B\u00FA\u016D\u00F9u\u01D6\u01D8\u01DA\u01DC\u00FC";

    //if (pinyinStr.matches("[a-z]*[1-5]")) {

    int tuneNumber = Character.getNumericValue(pinyinStr.charAt(pinyinStr.length() - 1));

    if (tuneNumber == 5) {
      return pinyinStr.substring(0, pinyinStr.length() - 1);
    } else if (tuneNumber <=0 || tuneNumber > 4) {
      return pinyinStr.replaceAll("v", "ü");
    }

    int indexOfA = pinyinStr.indexOf(charA);
    int indexOfE = pinyinStr.indexOf(charE);
    int ouIndex = pinyinStr.indexOf(ouStr);

    if (-1 != indexOfA) {
      indexOfUnmarkedVowel = indexOfA;
      unmarkedVowel = charA;
    } else if (-1 != indexOfE) {
      indexOfUnmarkedVowel = indexOfE;
      unmarkedVowel = charE;
    } else if (-1 != ouIndex) {
      indexOfUnmarkedVowel = ouIndex;
      unmarkedVowel = ouStr.charAt(0);
    } else {
      for (int i = pinyinStr.length() - 1; i >= 0; i--) {
        if (String.valueOf(pinyinStr.charAt(i)).matches("["
            + allUnmarkedVowelStr + "]")) {
          indexOfUnmarkedVowel = i;
          unmarkedVowel = pinyinStr.charAt(i);
          break;
        }
      }
    }

    if ((defaultCharValue != unmarkedVowel)
        && (defaultIndexValue != indexOfUnmarkedVowel)) {
      int rowIndex = allUnmarkedVowelStr.indexOf(unmarkedVowel);
      int columnIndex = tuneNumber - 1;

      int vowelLocation = rowIndex * 5 + columnIndex;

      char markedVowel = allMarkedVowelStr.charAt(vowelLocation);

      StringBuffer resultBuffer = new StringBuffer();

      resultBuffer.append(pinyinStr.substring(0, indexOfUnmarkedVowel).replaceAll("v", "ü"));
      resultBuffer.append(markedVowel);
      resultBuffer.append(pinyinStr.substring(indexOfUnmarkedVowel + 1, pinyinStr.length() - 1).replaceAll("v", "ü"));

      return resultBuffer.toString();

    } else
    // error happens in the procedure of locating vowel
    {
      return pinyinStr;
    }
  }
}
