package fi.tkgwf.ruuvi.handler.impl;

import fi.tkgwf.ruuvi.bean.HCIData;
import fi.tkgwf.ruuvi.bean.RuuviMeasurement;
import fi.tkgwf.ruuvi.config.Config;
import fi.tkgwf.ruuvi.handler.BeaconHandler;
import java.util.HashMap;
import java.util.Map;

public abstract class AbstractEddystoneURL implements BeaconHandler {

    private static final String RUUVI_BASE_URL = "ruu.vi/#";
    private final Map<String, Update> updatedMacs;
    private final long updateLimit = Config.getMeasurementUpdateLimit();
    private final boolean adaptiveUpdates = Config.getMeasurementUpdateAdaptiveness();
    private final Double fudge=0.5;
    public AbstractEddystoneURL() {
        updatedMacs = new HashMap<>();
    }

    abstract protected byte[] base64ToByteArray(String base64);

    @Override
    public RuuviMeasurement handle(HCIData hciData) {
        HCIData.Report.AdvertisementData adData = hciData.findAdvertisementDataByType(0x16);
        if (adData == null) {
            return null;
        }
        String hashPart = getRuuviUrlHashPart(adData.dataBytes());
        if (hashPart == null) {
            return null; // not a ruuvi url
        }
        byte[] data;
        try {
            data = base64ToByteArray(hashPart);
        } catch (IllegalArgumentException ex) {
            return null; // V2 format will throw this when trying to parse V4 and vice versa
        }
        if (data.length < 6 || data[0] != 2 && data[0] != 4) {
            return null; // unknown type
        }
        RuuviMeasurement measurement = new RuuviMeasurement();
        measurement.mac = hciData.mac;
        measurement.rssi = hciData.rssi;
        measurement.dataFormat = data[0] & 0xFF;

        measurement.humidity = ((double) (data[1] & 0xFF)) / 2d;

        int temperatureSign = (data[2] >> 7) & 1;
        int temperatureBase = (data[2] & 0x7F);
        double temperatureFraction = ((float) data[3]) / 100d;
        measurement.temperature = ((float) temperatureBase) + temperatureFraction;
        if (temperatureSign == 1) {
            measurement.temperature *= -1;
        }

        int pressureHi = data[4] & 0xFF;
        int pressureLo = data[5] & 0xFF;
        measurement.pressure = (double) pressureHi * 256 + 50000 + pressureLo;
        if( !shouldUpdate(measurement)) {
            return null;
        }
        return measurement;
    }

    private String getRuuviUrlHashPart(byte[] data) {
        if (data.length < 15) {
            return null; // too short
        }
        if ((data[0] & 0xFF) != 0xAA && (data[1] & 0xFF) != 0xFE) {
            return null; // not an eddystone UUID
        }
        if (data[2] != 0x10) {
            return null; // not an eddystone URL
        }
        if (data[4] != 0x03) {
            return null; // not https://
        }
        String basePart = new String(data, 5, data.length - (5));
        if (!basePart.startsWith(RUUVI_BASE_URL)) {
            return null; // not a ruuvi url
        }
        int preLength = 5 + RUUVI_BASE_URL.length();
        return new String(data, preLength, data.length - preLength);
    }

    private boolean shouldUpdate(RuuviMeasurement measurement) {
        String mac=measurement.mac;
        if (!Config.isAllowedMAC(mac)) {
            return false;
        }
        Update lastUpdate = updatedMacs.get(mac);

        if (lastUpdate == null || 
            (adaptiveUpdates && lastUpdate.differs(measurement)) ||
            lastUpdate.lastUpdate + updateLimit < System.currentTimeMillis()) 
        {
            Update update=Update.nextUpdate(lastUpdate,measurement);
            updatedMacs.put(mac, update);
            return true;
        }
        return false;
    }
    /**
     *  store context here for calculating predictions
     */
    static class Update
    {   
        static final int NMAX=200;
        protected int n=0;
        public Update()
        {}
        public Long lastUpdate;
        public Long lastLastUpdate;
        public Double[] jitter=new Double[RuuviMeasurement.getVectorLength()];
        public Double[] last=new Double[RuuviMeasurement.getVectorLength()];
        public Double[] lastLast=new Double[RuuviMeasurement.getVectorLength()];
        public boolean differs(RuuviMeasurement measurement)
        {
            boolean differs=false;
            long now=System.currentTimeMillis();
            Double[] current=measurement.getAsVector();
            Double[] predicted=new Double[current.length];
            //refactor this into a stream operation?
            for(int i=0;i<current.length;i++)
            {   
                predicted[i]=last[i]+(last[i]-lastLast[i])*(now-lastUpdate)/(lastUpdate-lastLastUpdate);
                Double delta=Math.abs(current[i]-predicted[i]);
                if(delta>Math.sqrt(jitter[i]))
                    differs|=true;
                jitter[i]=(jitter[i]*n+delta*delta)/(n+1);                
            }            
            if(n<NMAX)n++;
            return differs;
        }
        public static Update nextUpdate(Update last,RuuviMeasurement measurement)
        {
            if(last==null)
                last=new Update();
            return last.nextUpdate(measurement);
        }
        public Update nextUpdate(RuuviMeasurement measurement)
        {
            Update ret=new Update();
            long now=System.currentTimeMillis();
            ret.lastLastUpdate=(last==null)?now:lastUpdate;
            ret.lastLast=(last==null)?measurement.getAsVector():last;
            ret.lastUpdate=now;
            ret.last=measurement.getAsVector();
            return ret;
        }
    }
}   
