package obdii.starter.automotive.iot.ibm.com.iot4a_obdii;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothSocket;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.github.pires.obd.commands.fuel.FuelLevelCommand;
import com.github.pires.obd.commands.temperature.EngineCoolantTemperatureCommand;

import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.MqttAsyncClient;
import org.eclipse.paho.client.mqttv3.MqttCallbackExtended;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;
import java.util.UUID;
import java.util.concurrent.ExecutionException;

public class Home extends AppCompatActivity {
    final private int BT_PERMISSIONS_CODE = 000;

    BluetoothAdapter bluetoothAdapter = null;
    BluetoothDevice userDevice;

    Set<BluetoothDevice> pairedDevicesSet;
    final ArrayList<String> deviceNames = new ArrayList<>();
    final ArrayList<String> deviceAdresses = new ArrayList<>();
    String userDeviceAddress;

    private static final String TAG = BluetoothManager.class.getName();

    TextView fuelLevelValue;
    ProgressBar progressBar;

    protected static MqttAsyncClient mqtt;
    private static MqttConnectOptions options = new MqttConnectOptions();
    private static MemoryPersistence persistence = new MemoryPersistence();

    private JSONObject currentDevice;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_home);

        progressBar = new ProgressBar(this);
        progressBar.setVisibility(View.VISIBLE);
        progressBar.setIndeterminate(true);
        progressBar.setScaleX(0.5f);
        progressBar.setScaleY(0.5f);

        getSupportActionBar().setDisplayShowCustomEnabled(true);
        getSupportActionBar().setCustomView(progressBar);

        fuelLevelValue = (TextView) findViewById(R.id.fuelLevelValue);

        new API(getApplicationContext());

        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

        if (bluetoothAdapter == null) {
            Toast.makeText(getApplicationContext(), "Your device does not support Bluetooth!", Toast.LENGTH_SHORT).show();
        } else {
            int permissionGiven = 0;

            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                permissionGiven = checkSelfPermission(Manifest.permission.BLUETOOTH_ADMIN);
            }

            if (permissionGiven != PackageManager.PERMISSION_GRANTED) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    requestPermissions(new String[] {Manifest.permission.BLUETOOTH_ADMIN},
                            BT_PERMISSIONS_CODE);
                }
                return;
            } else {
                Log.i("Bluetooth Permissions", "Already Granted.");
                permissionsGranted();
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        switch (requestCode) {
            case BT_PERMISSIONS_CODE:
                if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    permissionsGranted();
                } else {
                    Toast.makeText(getApplicationContext(), "Permission Denied", Toast.LENGTH_SHORT).show();
                }
                break;
            default:
                super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        }
    }

    public void permissionsGranted() {
        if (!bluetoothAdapter.isEnabled()) {
            Intent enableBluetooth = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableBluetooth, 1);
        } else {
            pairedDevicesSet = bluetoothAdapter.getBondedDevices();

            if (pairedDevicesSet.size() > 0) {
                for (BluetoothDevice device : pairedDevicesSet)
                {
                    deviceNames.add(device.getName());
                    deviceAdresses.add(device.getAddress());
                }

                final AlertDialog.Builder alertDialog = new AlertDialog.Builder(this, R.style.AppCompatAlertDialogStyle);
                ArrayAdapter adapter = new ArrayAdapter(this, android.R.layout.select_dialog_singlechoice, deviceNames.toArray(new String[deviceNames.size()]));

                int selectedDevice = -1;
                for (int i = 0; i < deviceNames.size(); i++) {
                    if (deviceNames.get(i).toLowerCase().contains("obd")) {
                        selectedDevice = i;
                    }
                }

                alertDialog
                           .setCancelable(false)
                           .setSingleChoiceItems(adapter, selectedDevice, null)
                           .setTitle("Please Choose the OBDII Bluetooth Device")
                           .setPositiveButton("Ok", new DialogInterface.OnClickListener() {
                               @Override
                               public void onClick(DialogInterface dialog, int which)
                               {
                                   dialog.dismiss();
                                   int position = ((AlertDialog) dialog).getListView().getCheckedItemPosition();
                                   userDeviceAddress = deviceAdresses.get(position);

//                                   connectSocket(userDeviceAddress);
                                   checkDeviceRegistry();
                               }
                           })
                           .show();
            } else {
                Toast.makeText(getApplicationContext(), "Please pair with your OBDII device and restart the application!", Toast.LENGTH_SHORT).show();
            }
        }
    }

    public void checkDeviceRegistry() {
        String url = API.platformAPI + "/device/types/" + API.typeId + "/devices/" + userDeviceAddress.replaceAll(":", "-");

        getSupportActionBar().setTitle("Checking Device Registeration");
        progressBar.setVisibility(View.VISIBLE);

        try {
            API.doRequest task = new API.doRequest(new API.doRequest.TaskListener() {
                @Override
                public void postExecute(JSONArray result) throws JSONException {

                    JSONObject serverResponse = result.getJSONObject(result.length() - 1);
                    int statusCode = serverResponse.getInt("statusCode");

                    result.remove(result.length() - 1);

                    switch (statusCode) {
                        case 200:
                            Log.d("Check Device Registry", result.toString(4));
                            Log.d("Check Device Registry", "***Already Registered***");

                            getSupportActionBar().setTitle("Device Already Registered");
                            progressBar.setVisibility(View.GONE);

                            currentDevice = result.getJSONObject(0);
                            deviceRegistered();

                            break;
                        case 404:
                        case 405:
                            Log.d("Check Device Registry", "***Not Registered***");
                            progressBar.setVisibility(View.GONE);

                            final AlertDialog.Builder alertDialog = new AlertDialog.Builder(Home.this, R.style.AppCompatAlertDialogStyle);
                            alertDialog
                                    .setCancelable(false)
                                    .setTitle("Your Device is NOT Registered!")
                                    .setMessage("In order to use this application, we need to register your device to the IBM IoT Platform")
                                    .setPositiveButton("Ok", new DialogInterface.OnClickListener() {
                                        @Override
                                        public void onClick(DialogInterface dialogInterface, int which) {
                                            registerDevice();
                                        }
                                    })
                                    .setNegativeButton("Exit", new DialogInterface.OnClickListener() {
                                        @Override
                                        public void onClick(DialogInterface dialogInterface, int which) {
                                            Toast.makeText(Home.this, "Cannot continue without registering your device!", Toast.LENGTH_LONG).show();
                                            Home.this.finishAffinity();
                                        }
                                    })
                                    .show();
                    }
                }
            });

            task.execute(url, "GET", null, null, API.credentialsBase64).get();
        } catch (InterruptedException e) {
            e.printStackTrace();
        } catch (ExecutionException e) {
            e.printStackTrace();
        }
    }

    public void registerDevice() {
        String url = API.addDevices;

        getSupportActionBar().setTitle("Registering Your Device");
        progressBar.setVisibility(View.VISIBLE);

        try {
            API.doRequest task = new API.doRequest(new API.doRequest.TaskListener() {
                @Override
                public void postExecute(final JSONArray result) throws JSONException {
                    Log.d("Register Device", result.toString(4));

                    JSONObject serverResponse = result.getJSONObject(result.length() - 1);
                    int statusCode = serverResponse.getInt("statusCode");

                    result.remove(result.length() - 1);

                    switch (statusCode) {
                        case 201:
                        case 202:
                            final String authToken = result.getJSONObject(0).getString("authToken");
                            final String deviceId = result.getJSONObject(0).getString("deviceId");
                            final String sharedPrefsKey = "iota-obdii-auth-" + deviceId;

                            if (!API.getStoredData(sharedPrefsKey).equals(authToken)) {
                                API.storeData(sharedPrefsKey, authToken);
                            }

                            final AlertDialog.Builder alertDialog = new AlertDialog.Builder(Home.this, R.style.AppCompatAlertDialogStyle);
                            View authTokenAlert = getLayoutInflater().inflate(R.layout.activity_home_authtokenalert, null, false);

                            EditText authTokenField = (EditText) authTokenAlert.findViewById(R.id.authTokenField);
                            authTokenField.setText(authToken);

                            Button copyToClipboard = (Button) authTokenAlert.findViewById(R.id.copyToClipboard);
                            copyToClipboard.setOnClickListener(new View.OnClickListener() {
                                @Override
                                public void onClick(View view) {
                                    ClipboardManager clipboard = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
                                    ClipData clipData = ClipData.newPlainText("authToken", authToken);
                                    clipboard.setPrimaryClip(clipData);

                                    Toast.makeText(Home.this, "Successfully copied to your Clipboard!", Toast.LENGTH_SHORT).show();
                                }
                            });

                            alertDialog.setView(authTokenAlert);
                            alertDialog
                                    .setCancelable(false)
                                    .setTitle("Your Device is Now Registered!")
                                    .setMessage("Please take note of this Autentication Token as you will need it in the future")
                                    .setPositiveButton("Close", new DialogInterface.OnClickListener() {
                                        @Override
                                        public void onClick(DialogInterface dialogInterface, int which) {
                                            try {
                                                currentDevice = result.getJSONObject(0);
                                                deviceRegistered();
                                            } catch (JSONException e) {
                                                e.printStackTrace();
                                            }
                                        }
                                    })
                                    .show();
                            break;
                        default:
                            break;
                    }

                    progressBar.setVisibility(View.GONE);
                }
            });

            JSONArray bodyArray = new JSONArray();
            JSONObject bodyObject = new JSONObject();

            bodyObject
                    .put("typeId", API.typeId)
                    .put("deviceId", userDeviceAddress.replaceAll(":", "-"));

            bodyArray
                    .put(bodyObject);

            task.execute(url, "POST", null, bodyArray.toString(), API.credentialsBase64).get();
        } catch (InterruptedException e) {
            e.printStackTrace();
        } catch (ExecutionException e) {
            e.printStackTrace();
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    public void connectSocket(String userDeviceAddress) {
        final TextView fuelLevelValue = (TextView) findViewById(R.id.fuelLevelValue);
//        final TextView speedValue = (TextView) findViewById(R.id.speedValue);
        final TextView engineCoolantValue = (TextView) findViewById(R.id.engineCoolantValue);

        BluetoothAdapter btAdapter = BluetoothAdapter.getDefaultAdapter();
        BluetoothDevice device = btAdapter.getRemoteDevice(userDeviceAddress);
        userDevice = device;

        UUID uuid = UUID.fromString(API.getUUID());

        BluetoothSocket socket = null;

        Log.d(TAG, "Starting Bluetooth connection..");

        try {
            socket = device.createRfcommSocketToServiceRecord(uuid);
        } catch (Exception e) {
            Log.e("Bluetooth Connection", "Socket couldn't be created");
            e.printStackTrace();
        }

        try {
            socket.connect();
            Log.i("Bluetooth Connection", "CONNECTED");
        } catch (IOException e) {
            Log.e("Bluetooth Connection", e.getMessage());

            try {
                Log.i("Bluetooth Connection", "Using fallback method");

                socket = (BluetoothSocket) device.getClass().getMethod("createRfcommSocket", new Class[] {int.class}).invoke(device, 1);
                socket.connect();

                Log.i("Bluetooth Connection", "CONNECTED");
            }
            catch (Exception e2) {
                Log.e("Bluetooth Connection", "Couldn't establish connection");
            }
        }

        while (!Thread.currentThread().isInterrupted()) {
            try {
//                SpeedCommand speedCommand = new SpeedCommand();
//                speedCommand.run(socket.getInputStream(), socket.getOutputStream());
//
//                Log.d("Speed", speedCommand.getFormattedResult());
//                speedValue.setText(speedCommand.getFormattedResult());

                FuelLevelCommand fuelLevelCommand = new FuelLevelCommand();
                fuelLevelCommand.run(socket.getInputStream(), socket.getOutputStream());

                Log.d("Fuel Level", fuelLevelCommand.getFormattedResult());
                fuelLevelValue.setText(fuelLevelCommand.getFormattedResult());

                EngineCoolantTemperatureCommand engineCoolantTemperatureCommand = new EngineCoolantTemperatureCommand();
                engineCoolantTemperatureCommand.run(socket.getInputStream(), socket.getOutputStream());

                Log.d("Engine Coolant", engineCoolantTemperatureCommand.getFormattedResult());
                engineCoolantValue.setText(engineCoolantTemperatureCommand.getFormattedResult());
            } catch (IOException e) {
                e.printStackTrace();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    public void deviceRegistered() throws JSONException {
        final String clientIdPid = "d:" + API.orgId + ":" + API.typeId + ":" + currentDevice.getString("deviceId");
        final String broker      = "wss://" + API.orgId + ".messaging.internetofthings.ibmcloud.com:443";

        MemoryPersistence persistence = new MemoryPersistence();
        try {
            mqtt = new MqttAsyncClient(broker, clientIdPid, persistence);

            options.setCleanSession(true);
            options.setUserName("use-token-auth");
            options.setPassword(API.getStoredData("iota-obdii-auth-" + currentDevice.getString("deviceId")).toCharArray());
            options.setKeepAliveInterval(90);
            options.setAutomaticReconnect(true);

            mqtt.setCallback(new MqttCallbackExtended() {
                @Override
                public void connectComplete(boolean reconnect, String serverURI) {
                    // Subscriptions
                    if (reconnect){
                        Log.d("MQTT", "Automatically Reconnected " + serverURI);
                    } else {
                        Log.d("MQTT", "Connected for the first time! " + serverURI);
                    }
                }

                public void messageArrived(String topic, MqttMessage message) throws Exception {
                    // Not used
                }

                public void deliveryComplete(IMqttDeliveryToken token) {
                    // Not used
                }

                public void connectionLost(Throwable cause) {
                    Log.e("MQTT", "Connection Lost - " + cause.getMessage());
                }
            });

            Log.i("MQTT", "Connecting to broker: " + broker + " " + API.getStoredData("iota-obdii-auth-" + currentDevice.getString("deviceId")));
            mqtt.connect(options);

            Timer timer = new Timer();
            timer.scheduleAtFixedRate(new TimerTask() {

                @Override
                public void run() {
                    try {
                        if (mqtt.isConnected()) {
                            mqttPublish();
                        } else {
                            Log.e("MQTT", "No Connection to the Server");
                        }

                    } catch (MqttException e) {
                        e.printStackTrace();
                    }
                }
            }, 5000, 5000);

        } catch(MqttException me) {
            Log.e("Reason", me.getReasonCode() + "");
            Log.e("Message", me.getMessage());
            Log.e("Localized Message", me.getLocalizedMessage());
            Log.e("Cause", me.getCause() + "");
            Log.e("Exception", me + "");

            me.printStackTrace();
        }
    }

    public void mqttPublish() throws MqttException {
        ArrayList<ArrayList<String>> data = new ArrayList<ArrayList<String>>();
        data.add(new ArrayList<String>(Arrays.asList("fuelLevel", 30 + "")));
        data.add(new ArrayList<String>(Arrays.asList("engineCoolant", 92 + "")));

        String stringData = jsonToString(data);
        MqttMessage message = new MqttMessage(stringData.getBytes());

        try {
            mqtt.publish("iot-2/evt/fuelAndCoolant/fmt/format_string", message);
            Log.e("MQTT - Published", data.toString());
        } catch (MqttException e) {
            e.printStackTrace();
        }
    }

    public String jsonToString(ArrayList<ArrayList<String>> data) {
        String temp = "{\"d\":{";
        int accum = 0;

        for (int i=0; i < data.size(); i++) {
            if (accum == (data.size() - 1)) {
                temp += "\"" + data.get(i).get(0) + "\": \"" + data.get(i).get(1) + "\"}}";
            } else {
                temp += "\"" + data.get(i).get(0) + "\": \"" + data.get(i).get(1) + "\", ";
            }

            accum += 1;
        }

        return temp;
    }
}