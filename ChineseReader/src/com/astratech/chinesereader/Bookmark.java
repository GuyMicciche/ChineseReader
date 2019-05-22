package com.astratech.chinesereader;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileReader;
import java.io.FileWriter;
import java.util.ArrayList;

/**
 * Created by lastant on 7/20/2016.
 */
public class Bookmark {
  public long mPosition;
  public String mTitle;
  public int mLine;
  public int mWord;

  public Bookmark(long position, String title) {
    mPosition = position;
    mTitle = title;
  }

  public void setAnnotatedPosition(int line, int word) {
    mLine = line;
    mWord = word;
  }

  public static ArrayList<Bookmark> readFromFile(String filePath) {
    ArrayList<Bookmark> bookmarks = new ArrayList<>();

    BufferedReader br = null;
    try {
      br = new BufferedReader(new FileReader(filePath));
      bookmarks.clear();
      String line;
      while ((line = br.readLine()) != null) {
        int bmPos = Integer.parseInt(line);
        String bmTitle = br.readLine();
        br.readLine();
        bookmarks.add(new Bookmark(bmPos, bmTitle));
      }
    } catch (Exception e) { }

    return  bookmarks;
  }

  public static boolean saveToFile(ArrayList<Bookmark> bookmarks, String filePath) {
    BufferedWriter bw;
    try {
      bw = new BufferedWriter(new FileWriter(filePath));
      for (Bookmark bm : bookmarks) {
        bw.write(Long.toString(bm.mPosition));
        bw.write('\n');
        bw.write(bm.mTitle);
        bw.write('\n');
        bw.write('\n');
      }
      bw.flush();
      bw.close();
    } catch (Exception e) {
      return false;
    }

    return true;
  }

  public static Bookmark search(long position, ArrayList bookmarks) {
    if (bookmarks == null)
      return null;

    int lo = 0;
    int hi = bookmarks.size() - 1;
    while (lo <= hi) {
      int mid = lo + (hi - lo) / 2;
      long res = ((Bookmark)bookmarks.get(mid)).mPosition;
      if      (res > position) hi = mid - 1;
      else if (res < position) lo = mid + 1;
      else return (Bookmark)bookmarks.get(mid);
    }
    return null;
  }

  public static int searchClosest(long position, ArrayList bookmarks) {
    if (bookmarks == null)
      return -1;

    if (bookmarks.size() == 0)
      return 0;

    int lo = 0;
    int hi = bookmarks.size() - 1;
    int mid = 0;
    while (lo <= hi) {
      mid = lo + (hi - lo) / 2;
      long res = ((Bookmark)bookmarks.get(mid)).mPosition;
      if      (res > position) hi = mid - 1;
      else if (res < position) lo = mid + 1;
      else return mid;
    }

    //if (((Bookmark)bookmarks.get(lo)).mPosition < position)
    //  lo++;

    return lo;
  }

  public static Bookmark searchAnnotated(int line, int word, ArrayList bookmarks) {
    int lo = 0;
    int hi = bookmarks.size() - 1;
    while (lo <= hi) {
      int mid = lo + (hi - lo) / 2;
      Bookmark res = (Bookmark)bookmarks.get(mid);
      if      (res.mLine > line || res.mLine == line && res.mWord > word) hi = mid - 1;
      else if (res.mLine < line || res.mLine == line && res.mWord < word) lo = mid + 1;
      else return res;
    }
    return null;
  }
}

