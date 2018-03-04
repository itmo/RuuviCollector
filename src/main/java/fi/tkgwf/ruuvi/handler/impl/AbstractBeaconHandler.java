package fi.tkgwf.ruuvi.handler.impl;




public abstract class AbstractBeaconHandler implements BeaconHandler
{
    protected final long updateLimit = Config.getMeasurementUpdateLimit();
    /**
     * Contains the MAC address as key, and the timestamp of last sent update as
     * value
     */
    private final Map<String, Long> updatedMacs;
    protected AbstractBeaconHandler()
    {
        updatedMacs = new HashMap<>();    
    }
    protected boolean shouldUpdate(String mac) {
        if (!Config.isAllowedMAC(mac)) {
            return false;
        }
        Long lastUpdate = updatedMacs.get(mac);
        if (lastUpdate == null || lastUpdate + updateLimit < System.currentTimeMillis()) {
            updatedMacs.put(mac, System.currentTimeMillis());
            return true;
        }
        return false;
    }
    
}