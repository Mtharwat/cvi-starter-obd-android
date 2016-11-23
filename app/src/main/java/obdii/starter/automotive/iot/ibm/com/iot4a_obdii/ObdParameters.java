/**
 * Copyright 2016 IBM Corp. All Rights Reserved.
 * <p>
 * Licensed under the IBM License, a copy of which may be obtained at:
 * <p>
 * http://www14.software.ibm.com/cgi-bin/weblap/lap.pl?li_formnum=L-DDIN-AEGGZJ&popup=y&title=IBM%20IoT%20for%20Automotive%20Sample%20Starter%20Apps%20%28Android-Mobile%20and%20Server-all%29
 * <p>
 * You may not use this file except in compliance with the license.
 */

package obdii.starter.automotive.iot.ibm.com.iot4a_obdii;

import android.location.Location;
import android.support.annotation.NonNull;
import android.support.v7.app.AppCompatActivity;
import android.widget.TextView;

import com.github.pires.obd.commands.ObdCommand;
import com.github.pires.obd.commands.SpeedCommand;
import com.github.pires.obd.commands.engine.RPMCommand;
import com.github.pires.obd.commands.fuel.FuelLevelCommand;
import com.github.pires.obd.commands.temperature.EngineCoolantTemperatureCommand;
import com.github.pires.obd.commands.engine.OilTempCommand;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.List;

/**
 * Obd Parameters
 */

public class ObdParameters {

    static String formatTemperature(double temperature) {
        double imperialUnit = temperature * 1.8f + 32;

        return String.format("%.1f%s", temperature, "C") + " (" + String.format("%.1f%s", imperialUnit, "F") + ")";
    }

    static String formatSpeed(double speed) {
        double imperialUnit = speed * 0.621371192f;

        return String.format("%.1f%s", speed, "km/h") + " (" + String.format("%.1f%s", imperialUnit, "mph") + ")";
    }

    @NonNull
    static public List<ObdParameter> getObdParameterList(final AppCompatActivity activity) {
        final List<ObdParameter> obdParameters = new ArrayList<ObdParameter>();

        final ObdParameter engineRPM = new ObdParameter((TextView) activity.findViewById(R.id.engineRPMValue), activity, "Engine RPM", new RPMCommand()) {
            private long engineRPM = Math.round(Math.random() * 600) + 600;
            private String valueText;

            @Override
            protected void fetchValue(ObdCommand obdCommand, boolean simulation) {
                if (simulation) {
                    engineRPM = Math.round(Math.random() * 600) + 600;
                } else {
                    final RPMCommand rpmCommand = (RPMCommand) obdCommand;
                    engineRPM = rpmCommand.getRPM();
                }
                valueText = engineRPM + "";
            }

            @Override
            protected void setJsonProp(JsonObject json) {
                json.addProperty("engineRPM", engineRPM + "");
            }

            @Override
            protected String getValueText() {
                return valueText;
            }
        };
        obdParameters.add(engineRPM);

        final ObdParameter speed = new ObdParameter((TextView) activity.findViewById(R.id.speedValue), activity, "Speed", new SpeedCommand()) {
            private double speed = 0;
            private double delta = 10.0;
            private String valueText;

            @Override
            protected void fetchValue(ObdCommand obdCommand, boolean simulation) {
                if (simulation) {
                    if (speed >= 150.0) {
                        delta = -10.0;
                    } else if (speed <= 0.0) {
                        speed = 0.0;
                        delta = 10.0;
                    } else if (Math.random() < 0.2) {
                        delta *= -1.0;
                    }
                    speed += delta;

                } else {
                    final SpeedCommand speedCommand = (SpeedCommand) obdCommand;
                    speed = speedCommand.getMetricSpeed();
                }
                valueText = formatSpeed(speed);
            }


            @Override
            protected void setJsonProp(JsonObject json) {
                json.addProperty("speed", speed + "");
            }

            @Override
            protected String getValueText() {
                return valueText;
            }
        };
        obdParameters.add(speed);

        final ObdParameter engineOil = new ObdParameter((TextView) activity.findViewById(R.id.engineOilValue), activity, "Engine Oil", new OilTempCommand()) {
            private double engineOil = Math.random() * 120 + 20;
            private String valueText;

            @Override
            protected void fetchValue(ObdCommand obdCommand, boolean simulation) {
                if (simulation) {
                    engineOil = Math.random() * (140 - engineOil - 10) + engineOil - 10;

                } else {
                    final OilTempCommand oilTempCommand = (OilTempCommand) obdCommand;
                    engineOil = oilTempCommand.getTemperature();
                }
                valueText = formatTemperature(engineOil);
            }


            @Override
            protected void setJsonProp(JsonObject json) {
                json.addProperty("engineOilTemp", engineOil + "");
            }

            @Override
            protected String getValueText() {
                return valueText;
            }
        };
        obdParameters.add(engineOil);

        final ObdParameter engineCoolant = new ObdParameter((TextView) activity.findViewById(R.id.engineCoolantValue), activity, "Engine Coolant", new EngineCoolantTemperatureCommand()) {
            private double engineCoolant = Math.random() * 120 + 20;
            private String valueText;

            @Override
            protected void fetchValue(ObdCommand obdCommand, boolean simulation) {
                if (simulation) {
                    engineCoolant = Math.random() * (140 - engineCoolant - 10) + engineCoolant - 10;
                } else {
                    final EngineCoolantTemperatureCommand engineCoolantTemperatureCommand = (EngineCoolantTemperatureCommand) obdCommand;
                    engineCoolant = engineCoolantTemperatureCommand.getTemperature();
                }
                valueText = formatTemperature(engineCoolant);
            }

            @Override
            protected void setJsonProp(JsonObject json) {
                json.addProperty("engineTemp", engineCoolant + "");
            }

            @Override
            protected String getValueText() {
                return valueText;
            }
        };
        obdParameters.add(engineCoolant);

        final ObdParameter fuelLevel = new ObdParameter((TextView) activity.findViewById(R.id.fuelLevelValue), activity, "Fuel Level", new FuelLevelCommand()) {
            private double fuelLevel = Math.floor(Math.random() * 95) + 5;
            private String valueText;

            @Override
            protected void fetchValue(ObdCommand obdCommand, boolean simulation) {
                if (simulation) {
                    if (--fuelLevel < 5) {
                        fuelLevel = 50;
                    }
                    valueText = Math.round(fuelLevel) + "%";
                } else {
                    final FuelLevelCommand fuelLevelCommand = (FuelLevelCommand) obdCommand;
                    fuelLevel = fuelLevelCommand.getFuelLevel();
                    valueText = Math.round(fuelLevel) + "%";
                }
            }

            @Override
            protected void setJsonProp(JsonObject json) {
                json.addProperty("fuelLevel", fuelLevel + "");
            }

            @Override
            protected String getValueText() {
                return valueText;
            }
        };
        obdParameters.add(fuelLevel);

        final ObdParameter longitude = new ObdParameter((TextView) activity.findViewById(R.id.longitudeValue), activity, "Longitude", null) {
            private double longitudeValue = 0.0;
            private String valueText;

            @Override
            protected void fetchValue(ObdCommand obdCommand, boolean simulation) {
                if (Home.location != null) {
                    longitudeValue = Home.location.getLongitude();
                }
                valueText = String.format("%1$.7f", longitudeValue);
            }

            @Override
            protected void setJsonProp(JsonObject json) {
                json.addProperty("lng", longitudeValue + "");
            }

            @Override
            protected String getValueText() {
                return valueText;
            }
        };
        obdParameters.add(longitude);

        final ObdParameter latitude = new ObdParameter((TextView) activity.findViewById(R.id.latitudeValue), activity, "Latitude", null) {
            private double latitude = 0.0;
            private String valueText;

            @Override
            protected void fetchValue(ObdCommand obdCommand, boolean simulation) {
                if (Home.location != null) {
                    latitude = Home.location.getLatitude();
                }
                valueText = valueText = String.format("%1$.7f", latitude);
            }

            @Override
            protected void setJsonProp(JsonObject json) {
                json.addProperty("lat", latitude + "");
            }

            @Override
            protected String getValueText() {
                return valueText;
            }
        };
        obdParameters.add(latitude);

        return obdParameters;
    }

}
