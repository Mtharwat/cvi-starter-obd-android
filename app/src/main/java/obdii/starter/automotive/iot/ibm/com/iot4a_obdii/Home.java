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

import android.location.Criteria;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Bundle;

import android.provider.Settings;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;

import android.util.Log;

import android.view.View;

import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.NumberPicker;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.github.pires.obd.commands.fuel.FuelLevelCommand;
import com.github.pires.obd.commands.protocol.EchoOffCommand;
import com.github.pires.obd.commands.protocol.LineFeedOffCommand;
import com.github.pires.obd.commands.protocol.SelectProtocolCommand;
import com.github.pires.obd.commands.protocol.TimeoutCommand;
import com.github.pires.obd.commands.temperature.EngineCoolantTemperatureCommand;
import com.github.pires.obd.enums.ObdProtocols;

import com.google.gson.JsonObject;

import com.ibm.iotf.client.device.DeviceClient;

import org.eclipse.paho.client.mqttv3.MqttException;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;
import java.util.UUID;
import java.util.concurrent.ExecutionException;

public class Home extends AppCompatActivity implements LocationListener {
    LocationManager locationManager;
    Location location = null;
    String provider;

    private final int INITIAL_PERMISSIONS = 000;

    Map<String, String> permissions = new HashMap<>();
    ArrayList<String> permissionNeeded = new ArrayList<>();

    private boolean permissionsGranted = false;
    private boolean simulation = false;

    private final int GPS_INTENT = 000;
    private final int SETTINGS_INTENT = 001;

    private boolean networkIntentNeeded = false;

    BluetoothAdapter bluetoothAdapter = null;
    BluetoothDevice userDevice;

    private BluetoothSocket socket = null;
    private boolean socketConnected = false;

    Set<BluetoothDevice> pairedDevicesSet;
    ArrayList<String> deviceNames = new ArrayList<>();
    ArrayList<String> deviceAdresses = new ArrayList<>();
    private String userDeviceAddress;
    private String userDeviceName;

    private DeviceClient deviceClient = null;

    private static final String TAG = BluetoothManager.class.getName();

    private String trip_id;

    private TextView engineCoolantValue;
    private TextView fuelLevelValue;
    private ProgressBar progressBar;
    private Button changeNetwork;
    private Button changeFrequency;

    private float fuelLevel;
    private float engineCoolant;

    private double randomFuelLevel = Math.floor(Math.random() * 95) + 5;
    private double randomEngineCoolant = Math.floor(Math.random() * 120) + 20;

    private int timerDelay = 5000;
    private int timerPeriod = 15000;
    private Timer timer;

    private JSONObject currentDevice;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_home);

        locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        provider = locationManager.getBestProvider(new Criteria(), false);

        progressBar = new ProgressBar(this);
        progressBar.setVisibility(View.GONE);
        progressBar.setIndeterminate(true);
        progressBar.setScaleX(0.5f);
        progressBar.setScaleY(0.5f);

        getSupportActionBar().setDisplayShowCustomEnabled(true);
        getSupportActionBar().setCustomView(progressBar);

        engineCoolantValue = (TextView) findViewById(R.id.engineCoolantValue);
        fuelLevelValue = (TextView) findViewById(R.id.fuelLevelValue);

        changeNetwork = (Button) findViewById(R.id.changeNetwork);
        changeFrequency = (Button) findViewById(R.id.changeFrequency);

        new API(getApplicationContext());

        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if (bluetoothAdapter == null) {
            Toast.makeText(getApplicationContext(), "Your device does not support Bluetooth!", Toast.LENGTH_SHORT).show();

            changeNetwork.setEnabled(false);
            changeFrequency.setEnabled(false);

            getSupportActionBar().setTitle("Bluetooth Failed");
        } else {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                if (checkSelfPermission(Manifest.permission.INTERNET) != PackageManager.PERMISSION_GRANTED)
                    permissions.put("internet", Manifest.permission.INTERNET);

                if (checkSelfPermission(Manifest.permission.ACCESS_NETWORK_STATE) != PackageManager.PERMISSION_GRANTED)
                    permissions.put("networkState", Manifest.permission.ACCESS_NETWORK_STATE);

                if (checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED)
                    permissions.put("coarseLocation", Manifest.permission.ACCESS_COARSE_LOCATION);

                if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED)
                    permissions.put("fineLocation", Manifest.permission.ACCESS_FINE_LOCATION);

                if (checkSelfPermission(Manifest.permission.BLUETOOTH_ADMIN) != PackageManager.PERMISSION_GRANTED)
                    permissions.put("bluetooth", Manifest.permission.BLUETOOTH_ADMIN);

                for (Map.Entry<String, String> entry : permissions.entrySet()) {
                    permissionNeeded.add(entry.getValue());
                }

                if (permissionNeeded.size() > 0) {
                    Object[] tempObjectArray = permissionNeeded.toArray();
                    String[] permissionsArray = Arrays.copyOf(tempObjectArray, tempObjectArray.length, String[].class);

                    requestPermissions(permissionsArray, INITIAL_PERMISSIONS);
                } else {
                    permissionsGranted = true;

                    permissionsGranted();
                }
            } else {
                if (!API.warningShown()) {
                    AlertDialog.Builder builder = new AlertDialog.Builder(this);
                    builder
                            .setTitle("Warning")
                            .setMessage("This app requires permissions to your Locations, Bluetooth and Storage settings.\n\n" +
                                    "If you are running the application to your phone from Android Studio, you will not be able to allow these permissions.\n\n" +
                                    "If that is the case, please install the app through the provided APK file."
                            )
                            .setPositiveButton("Ok", null)
                            .show();
                }

                permissionsGranted();
            }
        }
    }

    @Override
    protected void onDestroy() {
        stopPublishing();
        disconnectDevice();
        super.onDestroy();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] results) {
        switch (requestCode) {
            case INITIAL_PERMISSIONS:
                if (results[0] == PackageManager.PERMISSION_GRANTED) {
                    permissionsGranted();
                } else {
                    Toast.makeText(getApplicationContext(), "Permissions Denied", Toast.LENGTH_SHORT).show();
                }
                break;
            default:
                super.onRequestPermissionsResult(requestCode, permissions, results);
        }
    }

    public void permissionsGranted() {
        Thread thread = new Thread() {
            @Override
            public void run() {
                try {
                    while (!isInterrupted()) {
                        Thread.sleep(1000);

                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                if (simulation) {
                                    fuelLevelValue.setText(Math.round(randomFuelLevel) + "%");
                                    engineCoolantValue.setText(randomEngineCoolant + "C");
                                } else {
                                    if (socketConnected) {
                                        FuelLevelCommand fuelLevelCommand = new FuelLevelCommand();
                                        EngineCoolantTemperatureCommand engineCoolantTemperatureCommand = new EngineCoolantTemperatureCommand();

                                        try {
                                            fuelLevelCommand.run(socket.getInputStream(), socket.getOutputStream());
                                            Log.d("Fuel Level", fuelLevelCommand.getFormattedResult());
                                            fuelLevel = fuelLevelCommand.getFuelLevel();

                                            fuelLevelValue.setText(Math.round(fuelLevelCommand.getFuelLevel()) + "%");

                                            engineCoolantTemperatureCommand.run(socket.getInputStream(), socket.getOutputStream());
                                            Log.d("Engine Coolant", engineCoolantTemperatureCommand.getFormattedResult());
                                            engineCoolant = engineCoolantTemperatureCommand.getTemperature();

                                            engineCoolantValue.setText(engineCoolantTemperatureCommand.getFormattedResult());
                                        } catch (IOException e) {
                                            e.printStackTrace();
                                        } catch (InterruptedException e) {
                                            e.printStackTrace();
                                        }
                                    }
                                }
                            }
                        });
                    }
                } catch (InterruptedException e) {
                }
            }
        };

        thread.start();

        final AlertDialog.Builder alertDialog = new AlertDialog.Builder(this, R.style.AppCompatAlertDialogStyle);
        alertDialog
                .setCancelable(false)
                .setTitle("Do you want to try out a simulated version?")
                .setPositiveButton("Yes", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which)
                    {
                        dialog.dismiss();

                        simulation = true;

                        changeNetwork.setEnabled(false);

                        checkDeviceRegistry();
                    }
                })
                .setNegativeButton("No, I have a real OBDII Dongle", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which)
                    {
                        dialog.dismiss();

                        if (!bluetoothAdapter.isEnabled()) {
                            Intent enableBluetooth = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
                            startActivityForResult(enableBluetooth, 1);
                        } else {
                            pairedDevicesSet = bluetoothAdapter.getBondedDevices();

                            // In case user clicks on Change Network, need to repopulate the devices list
                            deviceNames = new ArrayList<>();
                            deviceAdresses = new ArrayList<>();

                            if (pairedDevicesSet.size() > 0) {
                                for (BluetoothDevice device : pairedDevicesSet)
                                {
                                    deviceNames.add(device.getName());
                                    deviceAdresses.add(device.getAddress());
                                }

                                final AlertDialog.Builder alertDialog = new AlertDialog.Builder(Home.this, R.style.AppCompatAlertDialogStyle);
                                ArrayAdapter adapter = new ArrayAdapter(Home.this, android.R.layout.select_dialog_singlechoice, deviceNames.toArray(new String[deviceNames.size()]));

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
                                                userDeviceName = deviceNames.get(position);

                                                progressBar.setVisibility(View.VISIBLE);
                                                getSupportActionBar().setTitle("Connecting to \"" + userDeviceName + "\"");

                                                connectSocket(userDeviceAddress);
                                            }
                                        })
                                        .show();
                            } else {
                                Toast.makeText(getApplicationContext(), "Please pair with your OBDII device and restart the application!", Toast.LENGTH_SHORT).show();
                            }
                        }
                    }
                })
                .show();
    }

    public void connectSocket(String userDeviceAddress) {
        final BluetoothAdapter btAdapter = BluetoothAdapter.getDefaultAdapter();
        final BluetoothDevice device = btAdapter.getRemoteDevice(userDeviceAddress);
        userDevice = device;

        final UUID uuid = UUID.fromString(API.getUUID());

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

            socketConnected = true;

            checkDeviceRegistry();
        } catch (IOException e) {
            Log.e("Bluetooth Connection", e.getMessage());

            try {
                Log.i("Bluetooth Connection", "Using fallback method");

                socket = (BluetoothSocket) device.getClass().getMethod("createRfcommSocket", new Class[] {int.class}).invoke(device, 1);
                socket.connect();

                Log.i("Bluetooth Connection", "CONNECTED");

                progressBar.setVisibility(View.GONE);

                new EchoOffCommand().run(socket.getInputStream(), socket.getOutputStream());
                new LineFeedOffCommand().run(socket.getInputStream(), socket.getOutputStream());
                new TimeoutCommand(125).run(socket.getInputStream(), socket.getOutputStream());
                new SelectProtocolCommand(ObdProtocols.AUTO).run(socket.getInputStream(), socket.getOutputStream());

                socketConnected = true;

                checkDeviceRegistry();
            }
            catch (Exception e2) {
                Log.e("Bluetooth Connection", "Couldn't establish connection");

                Toast.makeText(Home.this, "Unable to connect to the device, please make sure to choose the right network", Toast.LENGTH_LONG).show();

                progressBar.setVisibility(View.GONE);
                getSupportActionBar().setTitle("Connection Failed");
            }
        }
    }

    public void checkDeviceRegistry() {
        getAccurateLocation();

        String url = "";

        if (simulation) {
            url = API.platformAPI + "/device/types/" + API.typeId + "/devices/" + API.getUUID();
        } else {
            url = API.platformAPI + "/device/types/" + API.typeId + "/devices/" + userDeviceAddress.replaceAll(":", "-");
        }

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
                                    ClipboardManager clipboardManager = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
                                    ClipData clipData = ClipData.newPlainText("authToken", authToken);
                                    clipboardManager.setPrimaryClip(clipData);

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
                    .put("deviceId", simulation ? API.getUUID() : userDeviceAddress.replaceAll(":", "-"));

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

    public void deviceRegistered() {
        trip_id = new SimpleDateFormat("yyyy-MM-dd").format(new Date());
        trip_id += "-" + UUID.randomUUID();

        try {
            if (createDeviceClient() != null) {
                connectDevice();
                startPublishing();
            }
        } catch (MqttException e) {
            e.printStackTrace();
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    private DeviceClient createDeviceClient() throws JSONException {
        Properties options = new Properties();
        options.setProperty("org", API.orgId);
        options.setProperty("type", API.typeId);
        options.setProperty("id", currentDevice.getString("deviceId"));
        options.setProperty("auth-method", "token");
        options.setProperty("auth-token", API.getStoredData("iota-obdii-auth-" + currentDevice.getString("deviceId")));

        try {
            DeviceClient myClient = new DeviceClient(options);
            deviceClient = myClient;
            return myClient;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    private void connectDevice() throws MqttException {
        if (deviceClient != null && !deviceClient.isConnected()) {
            deviceClient.connect();
        }
    }

    private void disconnectDevice() {
        if (deviceClient != null && deviceClient.isConnected()) {
            deviceClient.disconnect();
        }
        deviceClient = null;
    }

    private void startPublishing() {
        // stop existing timer
        stopPublishing();

        // start new timer
        timer = new Timer();
        timer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                try {
                    if (simulation) {
                        if (--randomFuelLevel < 5)
                            randomFuelLevel = 50;
                        randomEngineCoolant = Math.floor(Math.random() * (140 - randomEngineCoolant - 10)) + randomEngineCoolant - 10;
                    }
                    mqttPublish();
                } catch (MqttException e) {
                    e.printStackTrace();
                }
            }
        }, timerDelay, timerPeriod);
    }

    private void stopPublishing() {
        if (timer != null) {
            timer.cancel();
            timer = null;
        }
    }

    public void mqttPublish() throws MqttException {
        if (location != null) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    progressBar.setVisibility(View.VISIBLE);
                    getSupportActionBar().setTitle(
                            simulation ? "Simulated Data is Being Sent" : "Live Data is Being Sent"
                    );
                }
            });

            // Normally, the connection is kept alive, but it is closed when interval is long. Reconnect in this case.
            connectDevice();

            JsonObject event = new JsonObject();
            JsonObject data = new JsonObject();
            event.add("d", data);
            data.addProperty("lat", location.getLatitude());
            data.addProperty("lng", location.getLongitude());
            data.addProperty("trip_id", trip_id);

            JsonObject props = new JsonObject();
            data.add("props", props);

            if (simulation) {
                props.addProperty("fuelLevel", randomFuelLevel + "");
                props.addProperty("engineTemp", randomEngineCoolant + "");
            } else {
                props.addProperty("fuelLevel", fuelLevel + "");
                props.addProperty("engineTemp", engineCoolant + "");
            }

            deviceClient.publishEvent("status", event, 0);
            System.out.println("SUCCESSFULLY POSTED......");
            Log.d("Posted", event.toString());
        } else {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    progressBar.setVisibility(View.VISIBLE);
                    getSupportActionBar().setTitle("Waiting to Find Location");
                }
            });
        }
    }

    public void changeFrequency(View view) {
        final AlertDialog.Builder alertDialog = new AlertDialog.Builder(Home.this, R.style.AppCompatAlertDialogStyle);
        View changeFrequencyAlert = getLayoutInflater().inflate(R.layout.activity_home_changefrequency, null, false);

        final NumberPicker numberPicker = (NumberPicker) changeFrequencyAlert.findViewById(R.id.numberPicker);
        numberPicker.setMinValue(5);
        numberPicker.setMaxValue(60);
        numberPicker.setValue(15);

        alertDialog.setView(changeFrequencyAlert);
        alertDialog
                .setCancelable(false)
                .setTitle("Change the Frequency of Data Being Sent (in Seconds)")
                .setPositiveButton("Ok", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int which) {
                        int newFrequency = numberPicker.getValue() * 1000;

                        if (newFrequency != timerPeriod) {
                            timerPeriod = newFrequency;
                            startPublishing();
                        }
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    public void changeNetwork(View view) {
        permissionsGranted();
    }

    @Override
    public void onStart() {
        super.onStart();
    }

    @Override
    public void onResume() {
        super.onResume();

        if (ActivityCompat.checkSelfPermission(getApplicationContext(), Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(getApplicationContext(), Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return;
        }

        locationManager.requestLocationUpdates(provider, 500, 1, this);
    }

    @Override
    public void onPause() {
        super.onPause();


        if (ActivityCompat.checkSelfPermission(getApplicationContext(), Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(getApplicationContext(), Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return;
        }

        locationManager.removeUpdates(this);
    }

    @Override
    public void onLocationChanged(Location location) {
        getAccurateLocation();
    }

    @Override
    public void onStatusChanged(String provider, int status, Bundle extras) {

    }

    @Override
    public void onProviderEnabled(String provider) {

    }

    @Override
    public void onProviderDisabled(String provider) {

    }

    private void getAccurateLocation() {
        ConnectivityManager connectivityManager = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo networkInfo = connectivityManager.getActiveNetworkInfo();

        if (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) && (networkInfo != null && networkInfo.isConnected())) {
            if (ActivityCompat.checkSelfPermission(getApplicationContext(), Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(getApplicationContext(), Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                return;
            }

            locationManager = (LocationManager) getApplicationContext().getSystemService(Context.LOCATION_SERVICE);

            List<String> providers = locationManager.getProviders(true);
            Location finalLocation = null;

            for (String provider : providers) {
                if (ActivityCompat.checkSelfPermission(getApplicationContext(), Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(getApplicationContext(), Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                    return;
                }

                Location lastKnown = locationManager.getLastKnownLocation(provider);

                if (lastKnown == null) {
                    continue;
                }

                if (finalLocation == null || (lastKnown.getAccuracy() < finalLocation.getAccuracy())) {
                    finalLocation = lastKnown;
                }
            }

            if (finalLocation == null) {
                Log.e("Location Data", "Not Working!");
            } else {
                Log.d("Location Data", finalLocation.getLatitude() + " " + finalLocation.getLongitude() + "");
                location = finalLocation;
            }
        } else {
            if (!locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                Toast.makeText(getApplicationContext(), "Please turn on your GPS", Toast.LENGTH_LONG).show();

                Intent gpsIntent = new Intent(android.provider.Settings.ACTION_LOCATION_SOURCE_SETTINGS);
                startActivityForResult(gpsIntent, GPS_INTENT);

                if (networkInfo == null) {
                    networkIntentNeeded = true;
                }
            } else {
                if (networkInfo == null) {
                    Toast.makeText(getApplicationContext(), "Please turn on Mobile Data or WIFI", Toast.LENGTH_LONG).show();

                    Intent settingsIntent = new Intent(Settings.ACTION_SETTINGS);
                    startActivityForResult(settingsIntent, SETTINGS_INTENT);
                }
            }
        }
    }

    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == GPS_INTENT) {
            if (networkIntentNeeded) {
                Toast.makeText(getApplicationContext(), "Please connect to a network", Toast.LENGTH_LONG).show();

                Intent settingsIntent = new Intent(Settings.ACTION_SETTINGS);
                startActivityForResult(settingsIntent, SETTINGS_INTENT);
            } else {
                getAccurateLocation();
            }
        } else if (requestCode == SETTINGS_INTENT) {
            networkIntentNeeded = false;

            getAccurateLocation();
        }
    }
}