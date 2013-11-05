package subfragment;

import util.MyLocation;
import util.MyLocation.LocationResult;
import android.app.Activity;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.database.Cursor;
import android.location.Location;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentTransaction;
import android.support.v4.app.LoaderManager.LoaderCallbacks;
import android.support.v4.content.CursorLoader;
import android.support.v4.content.Loader;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.google.android.gms.maps.CameraUpdate;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.GoogleMap.OnMarkerClickListener;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptor;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.zoeas.qdeagubus.MainActivity.CallFragmentMethod;
import com.zoeas.qdeagubus.MyContentProvider;
import com.zoeas.qdeagubus.R;

public class GMapFragment extends Fragment implements CallFragmentMethod,
		LoaderCallbacks<Cursor>, OnMarkerClickListener {

	private SupportMapFragment mapFragment;
	private Context context;
	private GoogleMap map;
	private float density;
	private LatLng myLatLng;

	@Override
	public void onAttach(Activity activity) {
		super.onAttach(activity);
		context = activity;
	}

	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container,
			Bundle savedInstanceState) {
		View view = inflater.inflate(R.layout.fragment_gmap_layout, null);
		return view;
	}

	// 맵 생성
	@Override
	public void onActivityCreated(Bundle savedInstanceState) {
		super.onActivityCreated(savedInstanceState);
		density = context.getResources().getDisplayMetrics().density;

		FragmentManager fm = getChildFragmentManager();
		mapFragment = (SupportMapFragment) fm.findFragmentById(R.id.map);

		if (mapFragment == null) {
			FragmentTransaction ft = fm.beginTransaction();
			mapFragment = SupportMapFragment.newInstance();
			ft.replace(R.id.map, mapFragment);
			ft.commit();
		}

		if (map == null) {
			new Handler().postDelayed(new Runnable() {

				@Override
				public void run() {
					map = mapFragment.getMap();
					map.setMyLocationEnabled(true);
				}
			}, 3000);
		}

	}

	public void setGMap(String station_number, String station_name,
			LatLng latLng) {
		map.clear();
		BitmapDescriptor icon = BitmapDescriptorFactory
				.fromResource(R.drawable.busicon);

		map.addMarker(new MarkerOptions().title(station_name).position(latLng)
				.snippet(station_number).icon(icon).flat(true).anchor(0, 0)
				.rotation(0));
		map.setPadding(0, 0, 0, (int) (100 * density));
		Log.d("latLng", latLng.toString());
		CameraUpdate c = CameraUpdateFactory.newLatLngZoom(latLng, 15);
		map.animateCamera(c);

	}

	boolean cancle;
	// 생성될때가 아니라 자신이 선택될때 불려진다.
	// 인터페이스로 메인 ViewPager의 OnPageChangeListener 에서 호출한다.
	@Override
	public void OnCalled() {
		cancle = false; // 취소를 누르면 위치추적장소로 자동이동을 하지 않는다. 
		
		final ProgressDialog wait = new ProgressDialog(context); 
		wait.setButton(DialogInterface.BUTTON_NEGATIVE,"취소", new OnClickListener() {
			
			@Override
			public void onClick(DialogInterface dialog, int which) {
				cancle = true;
				dialog.dismiss();
			}
		});
		wait.setMessage("위치정보를 가져오고 있습니다");
		wait.show();

		// MyLocation 클래스 콜백 리스너. gps나 네트웤 위치 신호가 오기까지 기다리다가 onchange 리스너가 호출되면
		// 그 결과값을 gotLocation 메소드로 리턴해준다.
		LocationResult locationResult = new LocationResult() {
			@Override
			public void gotLocation(Location location) {

				if (map != null && location != null && !cancle) {
					wait.dismiss();
					myLatLng = new LatLng(location.getLatitude(),
							location.getLongitude());
					map.animateCamera(CameraUpdateFactory.newLatLngZoom(
							myLatLng, 17));

					getLoaderManager().initLoader(0, null, GMapFragment.this);
				}
			}
		};
		MyLocation myLocation = new MyLocation();
		myLocation.getLocation(context, locationResult, new Handler());
	}

	@Override
	public Loader<Cursor> onCreateLoader(int id, Bundle arg1) {
		Log.d("onCreateLoader", "called");

		Uri uri = MyContentProvider.CONTENT_URI;

		final double bound = 0.005;
		double maxlat = myLatLng.latitude + bound;
		double minlat = myLatLng.latitude - bound;
		double maxlnt = myLatLng.longitude + bound;
		double minlnt = myLatLng.longitude - bound;

		String[] projection = { "_id", "station_number", "station_name",
				"station_latitude", "station_longitude" };
		String selection = "(station_latitude BETWEEN " + minlat + " AND "
				+ maxlat + ") AND (station_longitude BETWEEN " + minlnt
				+ " AND " + maxlnt + ")";

		return new CursorLoader(context, uri, projection, selection, null, null);
	}

	@Override
	public void onLoadFinished(Loader<Cursor> loader, Cursor c) {
		Log.d("onLoadFininshed", "called");

		// 기존의 cursor를 그대로 불러오기 때문에 시작시 반드시 커서위치를 처음으로 되돌려줘야함
		c.moveToFirst();
		Log.d("counter", String.valueOf(c.getCount()));
		for (int i = 0; i < c.getCount(); i++) {
			String station_number = c.getString(1);
			String station_name = c.getString(2);
			double station_latitude = c.getDouble(3);
			double station_longitude = c.getDouble(4);
			LatLng boundLatLng = new LatLng(station_latitude, station_longitude);
			c.moveToNext();
			map.addMarker(new MarkerOptions().title(station_name)
					.snippet(station_number).position(boundLatLng));
			map.setOnMarkerClickListener(this);
		}
	}

	@Override
	public void onLoaderReset(Loader<Cursor> loader) {
		Log.d("loaderReset", "called");
	}

	@Override
	public boolean onMarkerClick(Marker marker) {
		OnSaveBusStationInfoListener saver = (OnSaveBusStationInfoListener) context;
		saver.OnSaveBusStationInfo(marker.getSnippet(), marker.getTitle(),
				new LatLng(0, 0));
		return false;
	}
}