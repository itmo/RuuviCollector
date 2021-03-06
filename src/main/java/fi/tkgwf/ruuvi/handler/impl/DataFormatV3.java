package fi.tkgwf.ruuvi.handler.impl;

import fi.tkgwf.ruuvi.bean.HCIData;
import fi.tkgwf.ruuvi.bean.RuuviMeasurement;
import fi.tkgwf.ruuvi.config.Config;
import fi.tkgwf.ruuvi.handler.BeaconHandler;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import org.apache.log4j.Logger;


public class DataFormatV3 extends AbstractBeaconHandler implements BeaconHandler {
    private static final Logger LOG = Logger.getLogger(DataFormatV3.class);

    private final int[] RUUVI_COPANY_IDENTIFIER = {0x99, 0x04}; // 0x0499

    public DataFormatV3() {
        super();
    }

    @Override
    public RuuviMeasurement handle(HCIData hciData) {
        HCIData.Report.AdvertisementData adData = hciData.findAdvertisementDataByType(0xFF);
        //LOG.debug("v3 update");
        if (adData == null ) {
            //LOG.debug("no ad v3");
            return null;
        }
        byte[] data = adData.dataBytes();
        if (data.length < 2 || (data[0] & 0xFF) != RUUVI_COPANY_IDENTIFIER[0] || (data[1] & 0xFF) != RUUVI_COPANY_IDENTIFIER[1]) {
            //LOG.debug("no 2 v3");
            return null;
        }
        data = Arrays.copyOfRange(data, 2, data.length); // discard the first 2 bytes, the company identifier
        if (data.length < 14 || data[0] != 3) {
            //LOG.debug("no 3 v3");
            return null;
        }
        RuuviMeasurement m = new RuuviMeasurement();
        m.mac = hciData.mac;
        m.rssi = hciData.rssi;
        m.dataFormat = data[0] & 0xFF;

        m.humidity = ((double) (data[1] & 0xFF)) / 2d;

        int temperatureSign = (data[2] >> 7) & 1;
        int temperatureBase = (data[2] & 0x7F);
        double temperatureFraction = ((float) data[3]) / 100d;
        m.temperature = ((float) temperatureBase) + temperatureFraction;
        if (temperatureSign == 1) {
            m.temperature *= -1;
        }

        int pressureHi = data[4] & 0xFF;
        int pressureLo = data[5] & 0xFF;
        m.pressure = (double) pressureHi * 256 + 50000 + pressureLo;

        m.accelerationX = (data[6] << 8 | data[7] & 0xFF) / 1000d;
        m.accelerationY = (data[8] << 8 | data[9] & 0xFF) / 1000d;
        m.accelerationZ = (data[10] << 8 | data[11] & 0xFF) / 1000d;

        int battHi = data[12] & 0xFF;
        int battLo = data[13] & 0xFF;
        m.batteryVoltage = (battHi * 256 + battLo) / 1000d;
        if(!shouldUpdate(m))
        {
            //LOG.debug("shouldnt update v3");
            return null;
        }
        //LOG.debug("sending v3 update");
        return m;
    }

}
