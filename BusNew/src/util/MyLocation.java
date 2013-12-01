package util;

import java.util.Timer;
import java.util.TimerTask;

import android.content.Context;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;

public class MyLocation {
    Timer timer1;
    LocationManager lm;
    LocationResult locationResult;
    boolean gps_enabled=false;
    boolean network_enabled=false;
    Location last;
    Handler handler;

    public boolean getLocation(Context context, LocationResult result,Handler handler)
    {
    	this.handler = handler;
        //I use LocationResult callback class to pass location value from MyLocation to user code.
        locationResult=result;
        if(lm==null)
            lm = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);

        //exceptions will be thrown if provider is not permitted.
        try{gps_enabled=lm.isProviderEnabled(LocationManager.GPS_PROVIDER);}catch(Exception ex){}
        try{network_enabled=lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER);}catch(Exception ex){}

        //don't start listeners if no provider is enabled
        if(!gps_enabled && !network_enabled){
        	Log.d("위치추적 클래스", "gps net 둘다 지원안함 크리");
            return false;
        }

        if(gps_enabled)
            lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0, 0, locationListenerGps);
        if(network_enabled)
            lm.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 0, 0, locationListenerNetwork);
        
        timer1=new Timer();
        timer1.schedule(new GetLastLocation(), 8000);
        return true;
    }

    LocationListener locationListenerGps = new LocationListener() {
        public void onLocationChanged(Location location) {
        	Log.d("MyLocation","gps 불려짐");
            timer1.cancel();
            locationResult.gotLocation(location);
            lm.removeUpdates(this);
            lm.removeUpdates(locationListenerNetwork);
        }
        public void onProviderDisabled(String provider) {}
        public void onProviderEnabled(String provider) {}
        public void onStatusChanged(String provider, int status, Bundle extras) {}
    };

    LocationListener locationListenerNetwork = new LocationListener() {
        public void onLocationChanged(Location location) {
        	Log.d("MyLocation","net 불려짐");
            timer1.cancel();
            locationResult.gotLocation(location);
            lm.removeUpdates(this);
            lm.removeUpdates(locationListenerGps);
        }
        public void onProviderDisabled(String provider) {}
        public void onProviderEnabled(String provider) {}
        public void onStatusChanged(String provider, int status, Bundle extras) {}
    };

    // 타임오버
    class GetLastLocation extends TimerTask {
        @Override
        public void run() {
        	Log.d("MyLocation","타임오버 불려짐");
             lm.removeUpdates(locationListenerGps);
             lm.removeUpdates(locationListenerNetwork);

             handler.post(new Runnable() {
				
				@Override
				public void run() {
					Location net_loc=null, gps_loc=null;
		             if(gps_enabled)
		                 gps_loc=lm.getLastKnownLocation(LocationManager.GPS_PROVIDER);
		             if(network_enabled)
		                 net_loc=lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);

		             //if there are both values use the latest one
		             if(gps_loc!=null && net_loc!=null){
		                 if(gps_loc.getTime()>net_loc.getTime())
		                     locationResult.gotLocation(gps_loc);
		                 else
		                     locationResult.gotLocation(net_loc);
		                 return;
		             }

		             if(gps_loc!=null){
		                 locationResult.gotLocation(gps_loc);
		                 return;
		             }
		             if(net_loc!=null){
		                 locationResult.gotLocation(net_loc);
		                 return;
		             }
		             locationResult.gotLocation(null);
				}
			});
             
        }
    }

    public static abstract class LocationResult{
        public abstract void gotLocation(Location location);
    }
    
    public void cancle(){
    	if(timer1 != null){
    		timer1.cancel();
    		lm.removeUpdates(locationListenerNetwork);
    		lm.removeUpdates(locationListenerGps);
    	}
    }
}
