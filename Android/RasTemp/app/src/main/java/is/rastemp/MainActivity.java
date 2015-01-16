package is.rastemp;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.support.v7.app.ActionBarActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import java.io.IOException;
import java.net.Socket;
import java.io.InputStream;

class RHTThread extends Thread{
    private final String TAG = getClass().getSimpleName();
    public String address = null;
    public boolean isConnected = false;
    public MainActivity ma;
    private int Res1 = 0, Res2 = 0, rh = 0, t = 0;

    public void error(int titleRes, int messageRes){
        Res1 = titleRes;
        Res2 = messageRes;
        ma.runOnUiThread(new Runnable(){public void run(){
            ma.alert.setTitle(Res1);
            ma.alert.setMessage(Res2);
            ma.alert.show();
            ma.updateStatus(R.string.Disconnected);
            activateButton();
        }});
        isConnected = false;
    }

    public void activateButton(){
        ma.runOnUiThread(new Runnable(){public void run(){
            ma.ConnBtn.setEnabled(true);
        }});
    }

    public void connBtnText(int textRes){
        activateButton();
        Res1 = textRes;
        ma.runOnUiThread(new Runnable(){public void run(){
            ma.ConnBtn.setText(Res1);
        }});
    }

    public void sleep(){
        try{Thread.sleep(100);}catch(InterruptedException ie){}
    }
    public void waitForData(InputStream i) throws IOException{
        while(i.available() == 0) sleep();
    }

    public boolean connect = false;
    public Socket sock;

    public void run(){
        Log.d(TAG, "Thread start");
        while(!Thread.currentThread().isInterrupted()){
            Log.d(TAG, "Waiting for request...");
            while(connect == false) sleep();
            connect = false;
            Log.d(TAG, "Connecting to server...");
            try {
                sock = new Socket(address, 4050);
            }catch (IOException ioe){
                Log.e(TAG, "Failed to connect: " + ioe.toString());
                error(R.string.FailedToConnect, R.string.FailedToConnectToServer);
                continue;
            }
            Log.d(TAG, "Connected!");
            isConnected = true;
            ma.runOnUiThread(new Runnable(){public void run(){ma.updateStatus(R.string.Connected);}});
            connBtnText(R.string.Disconnect);
            try {
                InputStream in = sock.getInputStream();
                Log.d(TAG, "Main loop...");
                while(isConnected == true) {
                    waitForData(in);
                    rh = in.read();
                    waitForData(in);
                    t = in.read();
                    Log.d(TAG, "rh = " + rh + ", t = " + t);
                    ma.runOnUiThread(new Runnable() {
                        public void run() {
                            ma.updateInfo(t, rh);
                        }
                    });
                }
                Log.d(TAG, "Disconnect request");
            } catch (IOException ioe) {
                Log.d(TAG, "Communication error: " + ioe.toString());
                error(R.string.ErrorOccur, R.string.CommunicationProblem);
            } finally {
                Log.d(TAG, "Cleaning up connection...");
                connBtnText(R.string.Connect);
                try {
                    sock.close();
                } catch (IOException ioe) {
                }
            }
        }
    }
}

public class MainActivity extends ActionBarActivity{
    private final String TAG = getClass().getSimpleName();
    public static final String PREFS_NAME = "RASTEMP";
    public AlertDialog.Builder alert;
    public RHTThread rhtt = null;
    public Button ConnBtn;
    private SharedPreferences spShared;

    public void updateStatus(int statusRes){
        getSupportActionBar().setTitle("RasTemp: " + getString(statusRes));
    }


    //온도와 습도정보를 화면에 반영
    public void updateInfo(int t, int rh){
        TextView Temp = (TextView) findViewById(R.id.temp);
        TextView RH = (TextView) findViewById(R.id.rh);
        Temp.setText(String.format("%d", t));
        RH.setText(String.format("%s %d%%", getString(R.string.RH), rh));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        rhtt = null;
        rhtt = new RHTThread();
        rhtt.ma = this;
        rhtt.start();
        updateStatus(R.string.Disconnected);
        alert = new AlertDialog.Builder(this);
        alert.setPositiveButton(R.string.Ok, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialog.dismiss();     //닫기
            }
        });
        ConnBtn = (Button) findViewById(R.id.connect);
        spShared = getSharedPreferences(PREFS_NAME, 0);
        TextView tv = (TextView) findViewById(R.id.servAddr);
        tv.setText(spShared.getString("lastAddr", ""));
    }


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item){
        return super.onOptionsItemSelected(item);
    }

    public void connectToServer(View v){
        ConnBtn.setEnabled(false);
        if(!rhtt.isConnected) {
            updateStatus(R.string.Connecting);
            Log.d(TAG, "Trying to connect...");
            TextView tv = (TextView) findViewById(R.id.servAddr);
            rhtt.address = tv.getText().toString();
            SharedPreferences.Editor e = spShared.edit();
            e.putString("lastAddr", rhtt.address);
            e.commit();

            if (rhtt.address.isEmpty()) {
                ConnBtn.setEnabled(true);
                alert.setTitle(R.string.AddressEmpty);
                alert.setMessage(R.string.EnterAddress);
                alert.show();
                updateStatus(R.string.Disconnected);
                return;
            }
            rhtt.connect = true;
        }else{
            rhtt.isConnected = false;
            updateStatus(R.string.Disconnected);
        }
    }

    public void onStop(){
        super.onStop();
        try {
            if(rhtt.sock != null)
                 rhtt.sock.close();
        }catch(IOException ioe){}
        rhtt.interrupt();
        try{
            if(rhtt.isAlive())
                rhtt.join(1000);
        }catch(InterruptedException ie){}
    }


}
