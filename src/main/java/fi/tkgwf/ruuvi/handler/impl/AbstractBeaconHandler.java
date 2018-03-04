package fi.tkgwf.ruuvi.handler.impl;

import fi.tkgwf.ruuvi.bean.RuuviMeasurement;
import fi.tkgwf.ruuvi.config.Config;
import fi.tkgwf.ruuvi.handler.BeaconHandler;
import java.util.Map;
import java.util.HashMap;
import org.apache.log4j.Logger;


public abstract class AbstractBeaconHandler implements BeaconHandler
{
    private static final Logger LOG = Logger.getLogger(AbstractBeaconHandler.class);
    protected final long updateLimit = Config.getMeasurementUpdateLimit();
    protected final boolean adaptiveUpdates = Config.getMeasurementUpdateAdaptiveness();
    protected static final Double fudge=5.0;
    /**
     * Contains the MAC address as key, and information 
     * of last updates sent as the value.
     */
    protected final Map<String, Update> updatedMacs;
    protected AbstractBeaconHandler()
    {
        updatedMacs = new HashMap<>();    
    }
    /**
     *  store context here for calculating predictions
     */
    public static class Update
    {   
        static final int NMAX=200;
        protected int n=0;
        public Update()
        {}
        public long lastUpdate=0;
        public long lastLastUpdate=0;
        public double[] jitter=new double[RuuviMeasurement.getVectorLength()];
        public double[] last=new double[RuuviMeasurement.getVectorLength()];
        public double[] lastLast=new double[RuuviMeasurement.getVectorLength()];
        public boolean differs(RuuviMeasurement measurement)
        {
            boolean differs=false;
            long now=System.currentTimeMillis();
            double[] current=measurement.getAsVector();
            double[] predicted=new double[current.length];
            //refactor this into a stream operation?
            for(int i=0;i<current.length;i++)
            {   
                LOG.debug("last:"+last[i]+"/ll:"+lastLast[i]);
                predicted[i]=last[i]+(last[i]-lastLast[i])*(now-lastUpdate)/(lastUpdate-lastLastUpdate+1);
                Double delta=Math.abs(current[i]-predicted[i]);
                if(delta>fudge*Math.sqrt(jitter[i]))
                    differs|=true;
                LOG.debug(""+i+":"+delta+">"+fudge*Math.sqrt(jitter[i]));
                jitter[i]=(jitter[i]*n+delta*delta)/(n+1); 
            }            
            if(n<NMAX)n++;
            //LOG.debug("differs:"+differs);
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
            ret.lastLastUpdate=(lastUpdate==0)?now:lastUpdate;
            ret.lastLast=(lastUpdate==0)?measurement.getAsVector():last;
            ret.lastUpdate=now;
            ret.last=measurement.getAsVector();
            ret.jitter=(double[])jitter.clone();
            return ret;
        }
    }
    protected boolean shouldUpdate(RuuviMeasurement measurement) {
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
}