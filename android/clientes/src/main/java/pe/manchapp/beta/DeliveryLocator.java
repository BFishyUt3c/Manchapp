package pe.manchapp.beta;

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.location.*;
import android.os.*;
import java.util.*;

/** One bounded, foreground-only fix. No background service and no saved GPS history. */
final class DeliveryLocator {
    static final int REQUEST=731;
    interface Result { void found(Location location);void failed(String message); }
    private final Activity activity;
    private final Handler handler=new Handler(Looper.getMainLooper());
    private LocationManager manager;
    private LocationListener listener;
    private Location best;
    private Result callback;
    private long started;
    private final Runnable timeout=()->complete();
    DeliveryLocator(Activity activity){this.activity=activity;}
    boolean permission(){return activity.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION)==PackageManager.PERMISSION_GRANTED||activity.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)==PackageManager.PERMISSION_GRANTED;}
    void ask(){activity.requestPermissions(new String[]{Manifest.permission.ACCESS_COARSE_LOCATION,Manifest.permission.ACCESS_FINE_LOCATION},REQUEST);}
    void start(Result result){cancel();callback=result;manager=(LocationManager)activity.getSystemService(Activity.LOCATION_SERVICE);started=SystemClock.elapsedRealtimeNanos();best=null;
        if(!permission()){fail("Puedes escribir tu dirección sin compartir tu ubicación.");return;}
        listener=new LocationListener(){@Override public void onLocationChanged(Location location){accept(location);}@Override public void onStatusChanged(String provider,int status,Bundle extras){}@Override public void onProviderEnabled(String provider){}@Override public void onProviderDisabled(String provider){}};
        int providers=0;for(String provider:new String[]{LocationManager.GPS_PROVIDER,LocationManager.NETWORK_PROVIDER})try{if(manager.isProviderEnabled(provider)){manager.requestLocationUpdates(provider,1000,0,listener,Looper.getMainLooper());providers++;}}catch(SecurityException|IllegalArgumentException ignored){}
        if(providers==0){fail("Activa la ubicación del celular o continúa con tu dirección escrita.");return;}handler.postDelayed(timeout,25000);
    }
    private void accept(Location location){if(location==null||!location.hasAccuracy()||location.getAccuracy()<=0||location.getAccuracy()>10000)return;long age=SystemClock.elapsedRealtimeNanos()-location.getElapsedRealtimeNanos();if(age<0||age>30_000_000_000L||location.getElapsedRealtimeNanos()<started-5_000_000_000L)return;if(best==null||location.getAccuracy()<best.getAccuracy())best=new Location(location);if(best.getAccuracy()<=50)complete();}
    private void complete(){Result result=callback;Location point=best;cancel();if(result!=null){if(point==null)result.failed("No pudimos obtener una ubicación reciente. Puedes seguir con tu dirección y referencias.");else result.found(point);}}
    private void fail(String message){Result result=callback;cancel();if(result!=null)result.failed(message);}
    void cancel(){handler.removeCallbacks(timeout);if(manager!=null&&listener!=null)try{manager.removeUpdates(listener);}catch(SecurityException ignored){}listener=null;callback=null;best=null;}
}
