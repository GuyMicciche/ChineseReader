package com.astratech.chinesereader;

//Project type now is Android library:
//  http://developer.android.com/guide/developing/projects/projects-eclipse.html#ReferencingLibraryProject

import java.io.File;
import java.io.FilenameFilter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Collections;
import java.util.Scanner;

//Android imports
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.StatFs;
import android.view.View.OnClickListener;
import android.view.ViewGroup.LayoutParams;
import android.view.*;
import android.widget.*;

public class FileBrowserActivity extends Activity {
	// Intent Action Constants
	public static final String INTENT_ACTION_SELECT_DIR = "com.astratech.chinesereader.SELECT_DIRECTORY_ACTION";
	public static final String INTENT_ACTION_SELECT_FILE = "com.astratech.chinesereader.SELECT_FILE_ACTION";

	// Intent parameters names constants
	public static final String startDirectoryParameter = "com.astratech.chinesereader.directoryPath";
	public static final String returnDirectoryParameter = "com.astratech.chinesereader.directoryPathRet";
	public static final String returnFileParameter = "com.astratech.chinesereader.filePathRet";
	public static final String showCannotReadParameter = "com.astratech.chinesereader.showCannotRead";
	public static final String filterExtension = "com.astratech.chinesereader.filterExtension";

	// Stores names of traversed directories
	ArrayList<Item> pathDirsList = new ArrayList<Item>();

	// Check if the first level of the directory structure is the one showing
	// private Boolean firstLvl = true;

	private static final String LOGTAG = "F_PATH";

  private List<Item> fileList = new ArrayList<Item>();
  private List<Item> sdList = new ArrayList<Item>();
	private File path = null;
	private String chosenFile;
	// private static final int DIALOG_LOAD_FILE = 1000;

	ArrayAdapter<Item> adapter;

	private boolean showHiddenFilesAndDirs = true;

	private boolean directoryShownIsEmpty = false;

	private String filterFileExtension = null;

	// Action constants
	private static int currentAction = -1;
	private static final int SELECT_DIRECTORY = 1;
	private static final int SELECT_FILE = 2;

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		// In case of
		// ua.com.vassiliev.androidfilebrowser.SELECT_DIRECTORY_ACTION
		// Expects com.mburman.fileexplore.directoryPath parameter to
		// point to the start folder.
		// If empty or null, will start from SDcard root.
		setContentView(R.layout.filebrowser);

		// Set action for this activity
		Intent thisInt = this.getIntent();
		currentAction = SELECT_DIRECTORY;// This would be a default action in
											// case not set by intent
		if (thisInt.getAction().equalsIgnoreCase(INTENT_ACTION_SELECT_FILE)) {
			currentAction = SELECT_FILE;
		}

		showHiddenFilesAndDirs = thisInt.getBooleanExtra(
				showCannotReadParameter, true);

		filterFileExtension = thisInt.getStringExtra(filterExtension);

		setInitialDirectory();
    getSdCards();

		parseDirectoryPath();

		this.createFileListAdapter();
		this.initializeButtons();
		this.initializeFileListView();

    loadFileList();
		updateCurrentDirectoryTextView();
	}

  private void getSdCards() {
    sdList.clear();

    sdList.add(new Item(System.getenv("EXTERNAL_STORAGE"), "Internal storage", R.drawable.folder_icon));
		String sSecondary = System.getenv("SECONDARY_STORAGE");

		if (sSecondary != null) {
			String[] sCards = sSecondary.split(":");
      int i = 1;
			for (String card : sCards) {
				File file = new File(card);
				if (file.canRead()) {
					sdList.add(new Item(card, "SD card" + (i > 1 ? " " + i : ""), R.drawable.folder_icon));
					i++;
				}
			}
		}
  }

	private void setInitialDirectory() {
		Intent thisInt = this.getIntent();
		String requestedStartDir = thisInt.getStringExtra(startDirectoryParameter);

		if (requestedStartDir != null && requestedStartDir.length() > 0) {// if(requestedStartDir!=null
			File tempFile = new File(requestedStartDir);
			if (tempFile.isDirectory())
				this.path = tempFile;
		}// if(requestedStartDir!=null

		if (this.path == null) {// No or invalid directory supplied in intent
								// parameter
			if (Environment.getExternalStorageDirectory().isDirectory()
					&& Environment.getExternalStorageDirectory().canRead()) {
        path = new File(Environment.getExternalStorageDirectory(), "/Pinyiner/");
        if (!path.canRead())
          path = new File(Environment.getExternalStorageDirectory(), "");
      }
			else {
        path = null;
      }
		}
	}// private void setInitialDirectory() {

	private void parseDirectoryPath() {
    pathDirsList.clear();

    File pathCopy = path;
    while (pathCopy != null && pathCopy.getParent() != null) {
      for (Item sdcard : sdList) {
        if (sdcard.file.equals(pathCopy.getAbsolutePath())) {
          pathDirsList.add(0, sdcard);
          return;
        }
      }

      pathDirsList.add(0, new Item(pathCopy.getAbsolutePath(), pathCopy.getName(), 0));
      pathCopy = pathCopy.getParentFile();
    }
	}

	private void initializeButtons() {
		Button upDirButton = (Button) this.findViewById(R.id.upDirectoryButton);
		upDirButton.setOnClickListener(new OnClickListener() {
			public void onClick(View v) {
        if (pathDirsList.size() == 0)
          return;

        // present directory removed from list
        pathDirsList.remove(pathDirsList.size() - 1);
        if (pathDirsList.size() > 0)
          path = new File(pathDirsList.get(pathDirsList.size() - 1).file);

        loadFileList();
        adapter.notifyDataSetChanged();
        updateCurrentDirectoryTextView();
      }
		});// upDirButton.setOnClickListener(

		Button selectFolderButton = (Button) this
				.findViewById(R.id.selectCurrentDirectoryButton);
		if (currentAction == SELECT_DIRECTORY) {
			selectFolderButton.setOnClickListener(new OnClickListener() {
				public void onClick(View v) {
					returnDirectoryFinishActivity();
				}
			});
		} else {// if(currentAction == this.SELECT_DIRECTORY) {
			selectFolderButton.setVisibility(View.GONE);
		}// } else {//if(currentAction == this.SELECT_DIRECTORY) {
	}// private void initializeButtons() {

	private void updateCurrentDirectoryTextView() {
		int i = 0;
		String curDirString = "";
		while (i < pathDirsList.size()) {
			curDirString += pathDirsList.get(i).title + "/";
			i++;
		}
		if (pathDirsList.size() == 0) {
			((Button) this.findViewById(R.id.upDirectoryButton))
					.setEnabled(false);
		} else
			((Button) this.findViewById(R.id.upDirectoryButton))
					.setEnabled(true);

		((TextView) this.findViewById(R.id.currentDirectoryTextView))
				.setText("Current directory: " + curDirString);
	}// END private void updateCurrentDirectoryTextView() {

	private void showToast(String message) {
		Toast.makeText(this, message, Toast.LENGTH_LONG).show();
	}

	private void initializeFileListView() {
		ListView lView = (ListView) this.findViewById(R.id.fileListView);
		lView.setBackgroundColor(Color.LTGRAY);
		LinearLayout.LayoutParams lParam = new LinearLayout.LayoutParams(
				LayoutParams.FILL_PARENT, LayoutParams.FILL_PARENT);
		lParam.setMargins(15, 5, 15, 5);
		lView.setAdapter(this.adapter);
		lView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
			public void onItemClick(AdapterView<?> parent, View view,
					int position, long id) {
        File sel = null;
        chosenFile = fileList.get(position).file;
        sel = new File(fileList.get(position).file);

				if (sel.isDirectory()) {
					if (sel.canRead()) {
						// Adds chosen directory to list
            if (pathDirsList.size() == 0) { //cards lsit
              pathDirsList.add(sdList.get(position));
              path = new File(sdList.get(position).file);
            } else {
              pathDirsList.add(new Item(chosenFile, new File(chosenFile).getName(), 0));
              path = new File(sel + "");
            }
						loadFileList();
						adapter.notifyDataSetChanged();
						updateCurrentDirectoryTextView();
					} else {// if(sel.canRead()) {
						showToast("Path does not exist or cannot be read");
					}// } else {//if(sel.canRead()) {
				}// if (sel.isDirectory()) {
					// File picked or an empty directory message clicked
				else {// if (sel.isDirectory()) {
					if (!directoryShownIsEmpty) {
						returnFileFinishActivity(sel.getAbsolutePath());
					}
				}// else {//if (sel.isDirectory()) {
			}// public void onClick(DialogInterface dialog, int which) {
		});// lView.setOnClickListener(
	}// private void initializeFileListView() {

	private void returnDirectoryFinishActivity() {
		Intent retIntent = new Intent();
		retIntent.putExtra(returnDirectoryParameter, path.getAbsolutePath());
		this.setResult(RESULT_OK, retIntent);
		this.finish();
	}// END private void returnDirectoryFinishActivity() {

	private void returnFileFinishActivity(String filePath) {
		Intent retIntent = new Intent();
		retIntent.putExtra(returnFileParameter, filePath);
		this.setResult(RESULT_OK, retIntent);
		this.finish();
	}// END private void returnDirectoryFinishActivity() {

	private void loadFileList() {
    fileList.clear();

    if (pathDirsList.size() == 0) {
      adapter.clear();
      for (int i = 0; i < sdList.size(); i++) {
        adapter.add(sdList.get(i));
      }

      return;
    }

		try {
			path.mkdirs();
		} catch (Exception e) {
      return;
		}

		if (path.exists() && path.canRead()) {
			FilenameFilter filter = new FilenameFilter() {
				public boolean accept(File dir, String filename) {
					File sel = new File(dir, filename);
					boolean showReadableFile = showHiddenFilesAndDirs
							|| sel.canRead();
					// Filters based on whether the file is hidden or not
					if (currentAction == SELECT_DIRECTORY) {
						return (sel.isDirectory() && showReadableFile);
					}
					if (currentAction == SELECT_FILE) {

						// If it is a file check the extension if provided
						if (sel.isFile() && filterFileExtension != null) {
							return (showReadableFile && sel.getName().endsWith(
									filterFileExtension));
						}
						return (showReadableFile);
					}
					return true;
				}// public boolean accept(File dir, String filename) {
			};// FilenameFilter filter = new FilenameFilter() {

			String[] fList = path.list(filter);
			this.directoryShownIsEmpty = false;
			for (int i = 0; i < fList.length; i++) {
				// Convert into file path
				File sel = new File(path, fList[i]);
				int drawableID = R.drawable.file_icon;
				boolean canRead = sel.canRead();
				// Set drawables
				if (sel.isDirectory()) {
					if (canRead) {
						drawableID = R.drawable.folder_icon;
					} else {
						drawableID = R.drawable.folder_icon_light;
					}
				}
				fileList.add(i, new Item(sel.getAbsolutePath(), fList[i], drawableID));
			}// for (int i = 0; i < fList.length; i++) {
			if (fileList.size() == 0) {
				this.directoryShownIsEmpty = true;
				fileList.add(0, new Item("", "Directory is empty", -1));
			} else {// sort non empty list
				Collections.sort(fileList, new ItemFileNameComparator());
			}
		} else {
		}
	}// private void loadFileList() {

	private void createFileListAdapter() {
		adapter = new ArrayAdapter<Item>(this,
				android.R.layout.select_dialog_item, android.R.id.text1,
				fileList) {
			@Override
			public View getView(int position, View convertView, ViewGroup parent) {
				// creates view
				View view = super.getView(position, convertView, parent);
				TextView textView = (TextView) view
						.findViewById(android.R.id.text1);
				// put the image on the text view
				int drawableID = 0;
				if (fileList.get(position).icon != -1) {
					// If icon == -1, then directory is empty
					drawableID = fileList.get(position).icon;
				}
				textView.setCompoundDrawablesWithIntrinsicBounds(drawableID, 0,
						0, 0);

				textView.setEllipsize(null);

				// add margin between image and text (support various screen
				// densities)
				// int dp5 = (int) (5 *
				// getResources().getDisplayMetrics().density + 0.5f);
				int dp3 = (int) (3 * getResources().getDisplayMetrics().density + 0.5f);
				// TODO: change next line for empty directory, so text will be
				// centered
				textView.setCompoundDrawablePadding(dp3);
				textView.setBackgroundColor(Color.LTGRAY);
				return view;
			}// public View getView(int position, View convertView, ViewGroup
		};// adapter = new ArrayAdapter<Item>(this,
	}// private createFileListAdapter(){

	private class Item {
		public String file;
    public String title;
		public int icon;

		public Item(String file, String title, Integer icon) {
			this.file = file;
      this.title = title;
			this.icon = icon;
		}

		@Override
		public String toString() {
			return title;
		}
	}// END private class Item {

	private class ItemFileNameComparator implements Comparator<Item> {
		public int compare(Item lhs, Item rhs) {
			return lhs.file.toLowerCase().compareTo(rhs.file.toLowerCase());
		}
	}

	public void onConfigurationChanged(Configuration newConfig) {
		super.onConfigurationChanged(newConfig);
		// Layout apparently changes itself, only have to provide good onMeasure
		// in custom components
		// TODO: check with keyboard
		// if(newConfig.keyboard == Configuration.KEYBOARDHIDDEN_YES)
	}// END public void onConfigurationChanged(Configuration newConfig) {

	public static long getFreeSpace(String path) {
		StatFs stat = new StatFs(path);
		long availSize = (long) stat.getAvailableBlocks()
				* (long) stat.getBlockSize();
		return availSize;
	}// END public static long getFreeSpace(String path) {

	public static String formatBytes(long bytes) {
		// TODO: add flag to which part is needed (e.g. GB, MB, KB or bytes)
		String retStr = "";
		// One binary gigabyte equals 1,073,741,824 bytes.
		if (bytes > 1073741824) {// Add GB
			long gbs = bytes / 1073741824;
			retStr += (new Long(gbs)).toString() + "GB ";
			bytes = bytes - (gbs * 1073741824);
		}
		// One MB - 1048576 bytes
		if (bytes > 1048576) {// Add GB
			long mbs = bytes / 1048576;
			retStr += (new Long(mbs)).toString() + "MB ";
			bytes = bytes - (mbs * 1048576);
		}
		if (bytes > 1024) {
			long kbs = bytes / 1024;
			retStr += (new Long(kbs)).toString() + "KB";
			bytes = bytes - (kbs * 1024);
		} else
			retStr += (new Long(bytes)).toString() + " bytes";
		return retStr;
	}// public static String formatBytes(long bytes){
}// END public class FileBrowserActivity extends Activity {
