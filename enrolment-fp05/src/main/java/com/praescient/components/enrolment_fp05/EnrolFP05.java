package com.praescient.components.enrolment_fp05;

import android.app.Dialog;
import android.app.DialogFragment;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.annotation.Nullable;
import android.util.Base64;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.Timer;
import java.util.TimerTask;

import android_serialport_api.AsyncFingerprint;
import android_serialport_api.SerialPortManager;

public class EnrolFP05 extends DialogFragment {

    private static Context context;
    private static Bundle args;
    private boolean showTitle = false;
    private boolean setCancelable = false;

    private LinearLayout popup;
    private ImageView preview;
    private TextView status;
    private TextView result;

    //Fingerprint Module
    private AsyncFingerprint vFingerprint;
    private byte[] model1 = new byte[512];
    private boolean isenrol = false;
    private boolean	bIsUpImage = true;
    private int	iFinger=0;
    private int count;
    private boolean bcheck = false;


    private Timer startTimer;
    private TimerTask startTask;
    private Handler startHandler;

    public EnrolFP05(){
        // Empty constructor is required for DialogFragment
        // Make sure not to add arguments to the constructor
        // Use `newInstance` instead as shown below
    }

    public static EnrolFP05 newInstance(Context c, String title){
        EnrolFP05 fragment = new EnrolFP05();
        args = new Bundle();
        args.putString("title", title);
        fragment.setArguments(args);
        context = c;
        return fragment;
    }

    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.popup, container, false);
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        //Fingerprint
        vFingerprint = SerialPortManager.getInstance().getNewAsyncFingerprint();

        iFinger = 1;

        FPInit();
        FPProcess();

        popup = (LinearLayout) view.findViewById(R.id.popup);
        status = (TextView) view.findViewById(R.id.status);
        result = (TextView) view.findViewById(R.id.result);
        preview = (ImageView) view.findViewById(R.id.placeholder);

        //Fetch argument from bundle and set title
        String title = getArguments().getString("title");
        getDialog().setTitle(title);

        if(args.containsKey("status")){
            status.setText(getArguments().getString("status"));
        }

        if(args.containsKey("result")){
            result.setText(getArguments().getString("result"));
        }

        if(args.containsKey("color")){
            popup.setBackgroundColor(context.getResources().getColor(getArguments().getInt("color")));
        }
    }

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {

        Dialog dialog = super.onCreateDialog(savedInstanceState);

        if(!showTitle){
            dialog.getWindow().requestFeature(Window.FEATURE_NO_TITLE);
        }

        if(!setCancelable){
            dialog.setCanceledOnTouchOutside(setCancelable);
        }

        return dialog;
    }

    public interface CaptureListener {
        void isCaptured(String fingerprint);
    }

    public boolean showTitle(boolean value){
        return showTitle = value;
    }

    public boolean cancelable(boolean value){
        return setCancelable = value;
    }

    public void setStatus(String value){
        args.putString("status", value);
    }

    public void setResult(String value){
        args.putString("result", value);
    }

    public void setBackgroundColor(int color){
        args.putInt("color", color);
    }


    private void FPProcess(){

        count = 1;
        try {
            Thread.currentThread();
            Thread.sleep(200);
        }catch (InterruptedException e)
        {
            e.printStackTrace();
        }
        vFingerprint.FP_GetImageEx();

    }

    private void FPInit(){

        vFingerprint.setOnGetImageExListener(new AsyncFingerprint.OnGetImageExListener() {
            @Override
            public void onGetImageExSuccess() {
                if(bcheck){
                    vFingerprint.FP_GetImageEx();
                }else{
                    if(bIsUpImage){
                        vFingerprint.FP_UpImageEx();
                        status.setText("Reading Fingerprint...");
                    }else{
                        status.setText("Processing...");
                        vFingerprint.FP_GenCharEx(count);
                    }
                }
            }

            @Override
            public void onGetImageExFail() {
                if(bcheck){
                    bcheck=false;
                    status.setText("Please place your finger on the fingerprint reader");
                    vFingerprint.FP_GetImageEx();
                    count++;
                }else{
                    vFingerprint.FP_GetImageEx();
                }
            }
        });

        vFingerprint.setOnUpImageExListener(new AsyncFingerprint.OnUpImageExListener() {
            @Override
            public void onUpImageExSuccess(byte[] data) {
                Bitmap image = BitmapFactory.decodeByteArray(data, 0, data.length);


                float density = context.getResources().getDisplayMetrics().density;
                int paddingUp = (int)(25 * density);
                int paddingBottom = (int)(22 * density);

                preview.setPadding(0,paddingUp,10,paddingBottom);

                preview.setImageBitmap(Bitmap.createScaledBitmap(image, 256, 330, false));
                vFingerprint.FP_GenCharEx(count);
                status.setText("Processing...");
            }

            @Override
            public void onUpImageExFail() {
            }
        });

        vFingerprint.setOnGenCharExListener(new AsyncFingerprint.OnGenCharExListener() {
            @Override
            public void onGenCharExSuccess(int bufferId) {
                if (bufferId == 1) {
                    bcheck=true;
                    status.setText("Please lift your finger！");
                    vFingerprint.FP_GetImageEx();
                } else if (bufferId == 2) {
                    vFingerprint.FP_RegModel();
                }
            }

            @Override
            public void onGenCharExFail() {
                status.setText("Fingerprint Capture Failed！");
            }
        });

        vFingerprint.setOnRegModelListener(new AsyncFingerprint.OnRegModelListener() {

            @Override
            public void onRegModelSuccess() {
                vFingerprint.FP_UpChar();
                status.setText("Synthetic template success！");
            }

            @Override
            public void onRegModelFail() {
                status.setText("Synthetic template failure！");
            }
        });

        vFingerprint.setOnUpCharListener(new AsyncFingerprint.OnUpCharListener() {

            @Override
            public void onUpCharSuccess(byte[] model) {

                if(iFinger == 1){

                    System.arraycopy(model, 0, model1, 0, 512);

                    isenrol = true;

                    if(isenrol){

                        byte[] template = new byte[model1.length];
                        CaptureListener activity = (CaptureListener) getActivity();


                        System.arraycopy(model1, 0, template , 0, model1.length);

                        String template64 = Base64.encodeToString(template, Base64.DEFAULT);
                        activity.isCaptured(template64);

                        status.setText("Capture Ok!");

                        SerialPortManager.getInstance().closeSerialPort();

                    } else {

                        status.setText("Capture Failed!");
                    }
                }


            }

            @Override
            public void onUpCharFail() {
                status.setText("Enrolment Failed!");
            }
        });
    }

    public void TimerStart(){
        if(startTimer==null){
            startTimer = new Timer();
            startHandler = new Handler() {
                @Override
                public void handleMessage(Message msg) {
                    super.handleMessage(msg);

                    TimeStop();
                    FPProcess();
                }
            };
            startTask = new TimerTask() {
                @Override
                public void run() {
                    Message message = new Message();
                    message.what = 1;
                    startHandler.sendMessage(message);
                }
            };
            startTimer.schedule(startTask, 1000, 1000);
        }
    }

    public void TimeStop(){
        if (startTimer!=null)
        {
            startTimer.cancel();
            startTimer = null;
            startTask.cancel();
            startTask=null;
        }
    }

    public void minimize(){
        SerialPortManager.getInstance().closeSerialPort();
    }

    public void maximize(){
        vFingerprint = SerialPortManager.getInstance().getNewAsyncFingerprint();
    }
}
