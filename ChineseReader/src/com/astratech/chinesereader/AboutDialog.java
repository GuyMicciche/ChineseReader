package com.astratech.chinesereader;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.view.Window;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import com.astratech.chinesereader.R;

/**
 * Created by lastant on 2/13/14.
 */
public class AboutDialog extends Dialog {
  Activity mActivity;

  public AboutDialog(Context context) {
    super(context);
    mActivity = (Activity)context;
  }

  public void onCreate(Bundle savedInstanceState) {
    requestWindowFeature(Window.FEATURE_NO_TITLE);
    setContentView(R.layout.about);
  }
}
