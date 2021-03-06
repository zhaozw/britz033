package sub.search.station;

import java.util.ArrayList;
import java.util.zip.Inflater;

import subfragment.CustomMapFragment;
import subfragment.CustomMapFragment.OnMapReadyListener;
import adapter.OnCommunicationReceive;
import adapter.SlidingMenuAdapter;
import android.app.Activity;
import android.content.Context;
import android.database.Cursor;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentTransaction;
import android.support.v4.app.ListFragment;
import android.support.v4.app.LoaderManager.LoaderCallbacks;
import android.support.v4.content.CursorLoader;
import android.support.v4.content.Loader;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.MeasureSpec;
import android.view.View.OnClickListener;
import android.view.View.OnKeyListener;
import android.view.ViewGroup;
import android.view.ViewStub;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.view.animation.Transformation;
import android.view.inputmethod.InputMethodManager;
import android.widget.AbsListView;
import android.widget.AbsListView.OnScrollListener;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.FrameLayout.LayoutParams;
import android.widget.ListView;
import android.widget.RelativeLayout;

import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.nineoldandroids.animation.Animator;
import com.nineoldandroids.animation.Animator.AnimatorListener;
import com.nineoldandroids.animation.ObjectAnimator;
import com.zoeas.qdeagubus.MainActivity;
import com.zoeas.qdeagubus.MainActivity.OnBackAction;
import com.zoeas.qdeagubus.MyContentProvider;
import com.zoeas.qdeagubus.R;
import com.zoeas.util.ActionMap;
import com.zoeas.util.AnimationRelativeLayout;
import com.zoeas.util.CalculateC;
import com.zoeas.util.LoopQuery;
import com.zoeas.util.ActionMap.OnActionInfoWindowClickListener;

public class SearchStationFragment extends ListFragment implements LoaderCallbacks<Cursor>, OnKeyListener,
		OnMapReadyListener, OnClickListener, OnBackAction, OnActionInfoWindowClickListener<Integer>, OnScrollListener, OnCommunicationReceive {

	private static final String TAG = "SearchStationFragment";
	
	public static final String TAG_STATION_MAP = "stationMap";
	public static final String KEY_SERARCH = "station";
	public static final String KEY_STATION_ID = "stationID";
	public static final String KEY_WIDE_LATITUDE = "wideLatitude";
	public static final String KEY_WIDE_LONGITUDE = "wideLongitude";
	public static final String KEY_BUS_ID = "BUSID";
	public static final String KEY_TAB_SELECTION = "STATION_SELETE";

	public static final int SEARCH_STATION = 0;
	public static final int SEARCH_WIDE = 1;
	public static final int SEARCH_STATION_PASSBUS = 2;

	// "_id", "station_number", "station_name", "station_longitude",
	// "station_latitude", "station_favorite"
	public static final int STATION_ID_INDEX = 0;
	public static final int STATION_NUMBER_INDEX = 1;
	public static final int STATION_NAME_INDEX = 2;
	public static final int STATION_LONGITUDE_INDEX = 3;
	public static final int STATION_LATITUDE_INDEX = 4;
	public static final int STATION_FAVORITE_INDEX = 5;

	private CalculateC cc;
	private StationSearchListCursorAdapter madapter;
	private RelativeLayout slidingBusListView;
	private LoopQuery<String> busNumloopQuery;
	private EditText et;
	private Context context;
	private AnimationRelativeLayout mapContainer;
	private InputMethodManager imm;
	private int currentUpdatableId; // 즐겨찾기시 모든 로더를 업데이트 하는 것을 방지
	private boolean isMarkerClick;
	private FrameLayout view;
	private ActionMap<Integer> actionMap;
	private boolean isGoogleServiceInstalled;
	private boolean isFirst;
	private boolean isReceived;
	private int rowHeight;
	private ArrayList<Marker> wideMarkerList;

	@Override
	public void onAttach(Activity activity) {
		super.onAttach(activity);
		context = activity;
		imm = (InputMethodManager) context.getSystemService(Context.INPUT_METHOD_SERVICE);
	}
	

	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
		view = (FrameLayout) inflater.inflate(R.layout.fragment_search_station_layout, null);
		isFirst = true;
		isReceived = false;
		wideMarkerList = new ArrayList<Marker>();
		cc = new CalculateC();
		
		et = (EditText) view.findViewById(R.id.edit_search_sub2fragment);
		et.addTextChangedListener(new MyWatcher());
		et.setOnKeyListener(this);
		et.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				if (mapContainer != null)
					mapContainer.hide();
				if (actionMap.isMap())
					actionMap.clearMap();
				Bundle search = new Bundle();
				search.putString(KEY_SERARCH, et.getText().toString());
				// 바닥제거
				resizeListView(0);
				getLoaderManager().restartLoader(SEARCH_STATION, search, SearchStationFragment.this);
			}
		});
		actionMap = new ActionMap<Integer>(context);
		mapContainer = (AnimationRelativeLayout) view.findViewById(R.id.layout_search_station_map_container);
		mapContainer.setInAnimation((Animation) AnimationUtils.loadAnimation(context, R.animator.in_ani));
		Button btn = (Button) view.findViewById(R.id.btn_search_station_widesearch);
		btn.setOnClickListener(this);

		if (!(isGoogleServiceInstalled = actionMap.checkGoogleService())) {
			View msgView = ((ViewStub) view.findViewById(R.id.viewstub_search_station_map_fail)).inflate();
			actionMap.setGoogleFailLayout(msgView);
		}
		

		return view;
	}

	@Override
	public void onActivityCreated(Bundle savedInstanceState) {
		super.onActivityCreated(savedInstanceState);
		getListView().setOnScrollListener(this);
		// 어뎁터 생성등록 커서는 없음.. 로더에서 추가
		madapter = new StationSearchListCursorAdapter(context, null, 0);
		setListAdapter(madapter);
		getLoaderManager().initLoader(SEARCH_STATION, null, this);
	}

	@Override
	public void onResume() {
		super.onResume();
		view.removeView(slidingBusListView);
		isMarkerClick = false;
		if (isGoogleServiceInstalled)
			setupMapIfNeeded();
	}

	private void setupMapIfNeeded() {
		if (!actionMap.isMap()) {
			FragmentManager fm = getChildFragmentManager();
			CustomMapFragment mapFragment = (CustomMapFragment) fm.findFragmentByTag(TAG_STATION_MAP);
			if (mapFragment == null) {
				mapFragment = CustomMapFragment.newInstance();
				FragmentTransaction ft = fm.beginTransaction();
				ft.add(R.id.layout_search_station_map, mapFragment, TAG_STATION_MAP);
				ft.commit();
			}
		}
	}

	// 맵이 준비되면 자동호출
	@Override
	public void OnMapReady(GoogleMap map) {
		actionMap.setMap(map);
		actionMap.moveMap(ActionMap.DEAGU_LATLNG);
		actionMap.setOnActionInfoWindowClickListener(this);
	}


	@Override
	public void onListItemClick(ListView l, View v, int position, long id) {
		super.onListItemClick(l, v, position, id);

		Cursor c = madapter.getCursor();
		c.moveToPosition(position);

		int stationId = c.getInt(STATION_ID_INDEX);
		String stationNumber = c.getString(STATION_NUMBER_INDEX);
		String stationName = c.getString(STATION_NAME_INDEX);
		double stationLongitude = cc.getData(c.getDouble(STATION_LONGITUDE_INDEX));
		double stationLatitude = cc.getData(c.getDouble(STATION_LATITUDE_INDEX));

		LatLng stationPosition = new LatLng(stationLatitude, stationLongitude);

		if (actionMap.isMap()) {
			mapContainer.show();
			
			MarkerOptions options = new MarkerOptions().position(stationPosition).title(stationName)
					.icon(BitmapDescriptorFactory.defaultMarker(120));
			actionMap.addMarkerAndShow(options, stationId);
			actionMap.aniMap(stationPosition, ActionMap.ZOOM_IN);
		}
	}

	// 하단 더미 패딩을 계산, 리스트뷰의 크기가 변할때마다 호출되게
	// 호출되는 시점, 주변검색때, 지도가 닫힐때,  닫힐때 -> 첫상태와(초기화 0으로) 스테이션검색상태(et.onclick)
	public void resizeListView(int dummyHeight) {
		madapter.setDummyHeight(dummyHeight);
	}

	public class DropDownAnim extends Animation {
		private final int targetHeight;
		private final View view;
		private final boolean down;

		public DropDownAnim(View view, int targetHeight, boolean down) {
			this.view = view;
			this.targetHeight = targetHeight;
			this.down = down;
		}

		@Override
		protected void applyTransformation(float interpolatedTime, Transformation t) {
			int newHeight;
			if (down) {
				newHeight = (int) (targetHeight * interpolatedTime);
			} else {
				newHeight = (int) (targetHeight * (1 - interpolatedTime));
			}
			view.getLayoutParams().height = newHeight;
			view.requestLayout();
		}

		@Override
		public void initialize(int width, int height, int parentWidth, int parentHeight) {
			super.initialize(width, height, parentWidth, parentHeight);
		}

		@Override
		public boolean willChangeBounds() {
			return true;
		}
	}

	// class MyScaler extends ScaleAnimation{
	// private View mView;
	// private LinearLayout.LayoutParams mLayoutParams;
	// private int mMarginBottomFromY;
	// private int mMarginBottomToY;
	// private boolean mVanishAfter;
	//
	// public MyScaler(float fromX, float toX, float fromY, float toY, int
	// duration, View view, boolean vanishAfter) {
	// super(fromX, toX, fromY, toY);
	// setDuration(duration);
	// mView =view;
	// mVanishAfter = vanishAfter;
	// mLayoutParams = (LinearLayout.LayoutParams) view.getLayoutParams();
	// int height = mView.getHeight();
	// mMarginBottomFromY = (int) (height * fromY) + mLayoutParams.bottomMargin
	// - height;
	// mMarginBottomToY = (int) (0 - ((height * toY) +
	// mLayoutParams.bottomMargin)) - height;
	// }
	//
	// @Override
	// protected void applyTransformation(float interpolatedTime,
	// Transformation t) {
	// super.applyTransformation(interpolatedTime, t);
	// if(interpolatedTime < 1.0f){
	// int newMarginBottom = mMarginBottomFromY + (int) ((mMarginBottomToY -
	// mMarginBottomFromY) * interpolatedTime);
	// mLayoutParams.setMargins(mLayoutParams.leftMargin,
	// mLayoutParams.topMargin, mLayoutParams.rightMargin, newMarginBottom);
	// mView.getParent().requestLayout();
	// } else if(mVanishAfter){
	// mView.setVisibility(View.VISIBLE);
	// }
	// }
	// }

	class MyWatcher implements TextWatcher {

		@Override
		public void afterTextChanged(Editable s) {
		}

		@Override
		public void beforeTextChanged(CharSequence s, int start, int count, int after) {
		}

		@Override
		public void onTextChanged(CharSequence s, int start, int before, int count) {
			// 데이터베이스 검색 하여 리스트뷰 새로 뿌림
			Bundle search = new Bundle();
			search.putString(KEY_SERARCH, s.toString());
			getLoaderManager().restartLoader(SEARCH_STATION, search, SearchStationFragment.this);
		}

	}

	@Override
	public Loader<Cursor> onCreateLoader(int id, Bundle args) {
		Log.d(TAG, "로더 생성자 호출됨 : " + id);
		Uri baseUri = MyContentProvider.CONTENT_URI_STATION;

		// 이것의 순서를 바꿔줄시 반드시 위의 상수인덱스 값도 변경해줘야함
		String[] projection = { "_id", "station_number", "station_name", "station_longitude", "station_latitude",
				"station_favorite" };
		String selection = null;

		switch (id) {
		// 커서어뎁터의 경우_id 안넣으면 에러 슈바
		case SEARCH_STATION:
			currentUpdatableId = id;
			madapter.resetAnimatedPosition();
			if (args != null) {
				selection = "station_name like '%" + args.getString(KEY_SERARCH) + "%' OR station_number like '%"
						+ args.getString(KEY_SERARCH) + "%'";
			}
			break;
		case SEARCH_WIDE:
			currentUpdatableId = id;
			madapter.resetAnimatedPosition();
			Log.d(TAG, "주변검색작동");
			double latitude = cc.setData(args.getDouble(KEY_WIDE_LATITUDE));
			double longitude = cc.setData(args.getDouble(KEY_WIDE_LONGITUDE));

			double bound = cc.setData(0.005);
			double minLatitude = latitude - bound;
			double maxLatitude = latitude + bound;
			double minLongitude = longitude - bound;
			double maxLongitude = longitude + bound;

			if (args != null) {
				selection = "(station_latitude BETWEEN " + minLatitude + " AND " + maxLatitude + ") AND ("
						+ "station_longitude BETWEEN " + minLongitude + " AND " + maxLongitude + ")";
			}
			break;
		case SEARCH_STATION_PASSBUS:
			projection = new String[] { "_id", "station_pass" };
			selection = "_id=" + args.getInt(KEY_STATION_ID);
			break;
		case LoopQuery.DEFAULT_LOOP_QUERY_ID:
			baseUri = MyContentProvider.CONTENT_URI_BUS;
			projection = new String[] { "_id", MyContentProvider.BUS_NUMBER, MyContentProvider.BUS_ID };
			selection = "bus_id=" + args.getString(LoopQuery.KEY);
			break;
		}

		return new CursorLoader(getActivity(), baseUri, projection, selection, null, null);
	}

	/**
	 * 앞서 생성된 커서를 받아옴, 업데이트시 모든 로더가 다 날아옴
	 * 뿐만 아니라 리셋후 다시 돌아올때마다 모든 로더를 다시 다 불러옴
	 * 이때 불러들이는 로더들은 각 로더별로 마지막에 호출된 것들
	 * 상황을 보고 createView에 flag를 만들어서 예상치 못한 재호출들은 다 차단해야한다
	 * 이것의 예를들면 
	 * search_station 외에는 재생성때 불려져선 안되니 다 차단
	 */
	@Override
	public void onLoadFinished(Loader<Cursor> loader, Cursor cursor) {
		

		Log.d(TAG, "로더 아이디: " + loader.getId());
		
		if(isFirst && loader.getId() != SEARCH_STATION){
			return;
		}
		
		switch (loader.getId()) {
		case SEARCH_STATION:
			if (currentUpdatableId == SEARCH_STATION) {
				resizeListView(0);
				madapter.swapCursor(cursor);
			}
			
			if (isFirst) {
				View childView = madapter.getView(0, null, getListView());
				childView.measure(MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED),
						MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED));
				rowHeight = childView.getMeasuredHeight();
				Log.d(TAG, "리스트뷰 아이템 높이" + rowHeight);
				isFirst = false;
			}
			
			if(isReceived){
			getListView().performItemClick(getListView().getChildAt(0), 0, madapter.getItemId(0));
			}
			
			break;
		case SEARCH_WIDE:
			if (currentUpdatableId == SEARCH_WIDE) {
				madapter.swapCursor(cursor);
				getListView().setItemChecked(0, true);
				cursor.moveToFirst();
				wideMarkerList.clear();
				for (int i = 0; i < cursor.getCount(); i++) {
					LatLng position = new LatLng(cc.getData(cursor.getDouble(4)), cc.getData(cursor.getDouble(3)));
					MarkerOptions options = new MarkerOptions().position(position).title(cursor.getString(2));
					wideMarkerList.add(actionMap.addMarker(options, cursor.getInt(0)));
					cursor.moveToNext();
				}
				actionMap.aniMapZoom(ActionMap.ZOOM_NOMAL);
				resizeListView(getListView().getHeight()-(rowHeight-(int)(3*getResources().getDisplayMetrics().density)));
			}
			break;
		case SEARCH_STATION_PASSBUS:
			if (isMarkerClick) {
				cursor.moveToFirst();
				settingSlidingMenuQuery(cursor.getString(1));
				isMarkerClick = false;
			}
			break;
		case LoopQuery.DEFAULT_LOOP_QUERY_ID:
			if (cursor.getCount() != 0) { // 대구버스통계가 뭐 같아서.. 아직 만들지도 않은 버스를 미리
											// 넣어놨을 경우 없다고 뜸 ㅡ,.ㅡ;;;
				cursor.moveToFirst();
				busNumloopQuery.addResultData(cursor.getString(1), cursor.getString(2)); // number,
																							// id
																							// 순서
			}
			if (!busNumloopQuery.isEnd()) {
				busNumloopQuery.restart();
			} else {
				finishSlidingMenuQuery();
			}
			break;
		}
	}

	private void settingSlidingMenuQuery(String passBusId) {
		String[] arrayBusId = passBusId.split(",");

		busNumloopQuery = new LoopQuery<String>(getLoaderManager(), arrayBusId, this);
		busNumloopQuery.start();
	}

	private void finishSlidingMenuQuery() {
		MainActivity.backAction.push();
		
		slidingBusListView = (RelativeLayout) LayoutInflater.from(context).inflate(R.layout.layout_sliding_menu, view, false);
		slidingBusListView.setClickable(true);
		ListView slidingList = (ListView) slidingBusListView.findViewById(R.id.listview_sliding_menu);
		SlidingMenuAdapter slidingAdapter = new SlidingMenuAdapter(context, busNumloopQuery.getResultData());
		slidingList.setOnItemClickListener(slidingAdapter);
		slidingList.setAdapter(slidingAdapter);
		view.addView(slidingBusListView);
		Animator ani = ObjectAnimator.ofFloat(slidingBusListView, "translationX", -300, 0);
		ani.setDuration(300);
		ani.start();
	}

	@Override
	public void onLoaderReset(Loader<Cursor> loader) {
		madapter.swapCursor(null);
	}

	@Override
	public boolean onKey(View v, int keyCode, KeyEvent event) {
		if (event.getAction() == KeyEvent.ACTION_DOWN && keyCode == KeyEvent.KEYCODE_ENTER) {
			imm.hideSoftInputFromWindow(et.getWindowToken(), InputMethodManager.HIDE_NOT_ALWAYS);
			return true;
		}

		return false;
	}

	// 주변검색
	@Override
	public void onClick(View v) {
		// 리스트 맨앞으로 돌림
		getListView().setSelectionAfterHeaderView();
		Bundle mapCenterCoordinate = new Bundle();
		LatLng centerLatLng = actionMap.getCenterOfMap();
		mapCenterCoordinate.putDouble(KEY_WIDE_LATITUDE, centerLatLng.latitude);
		mapCenterCoordinate.putDouble(KEY_WIDE_LONGITUDE, centerLatLng.longitude);
		actionMap.clearMap();
		getLoaderManager().restartLoader(SEARCH_WIDE, mapCenterCoordinate, this);
	}

	@Override
	public void onBackPressed() {
		Animator ani = ObjectAnimator.ofFloat(slidingBusListView, "translationX", 0, -300);
		ani.setDuration(100);

		ani.addListener(new AnimatorListener() {
			@Override
			public void onAnimationStart(Animator animation) {
			}

			@Override
			public void onAnimationRepeat(Animator animation) {
			}

			@Override
			public void onAnimationEnd(Animator animation) {
				view.removeView(slidingBusListView);
			}

			@Override
			public void onAnimationCancel(Animator animation) {
			}
		});
		ani.start();

	}

	@Override
	public void onClear() {
		view.removeView(slidingBusListView); // 만약 탭을 2단계 이상 즉시 이동할 경우
												// removeView는 동작치 않음
	}

	
	/** 마커 정보창 클릭시 버스리스트 나옴
	 * 쿼리루프를 돌면서 모든 버스리스트를 얻고
	 * finishSlidingMenuQuery에서 슬라이딩 작동
	 */
	
	@Override
	public void onInfoWindowClick(Marker marker, Integer id) {
		isMarkerClick = true;
		Bundle stationId = new Bundle();
		stationId.putInt(KEY_STATION_ID, id);
		getLoaderManager().restartLoader(SEARCH_STATION_PASSBUS, stationId, this);
	}

	@Override
	public void onScroll(AbsListView view, int firstVisibleItem, int visibleItemCount, int totalItemCount) {
		if (currentUpdatableId == SEARCH_WIDE) {
			getListView().setItemChecked(firstVisibleItem, true);
			if(wideMarkerList.size()!=0){
				Marker selectInfo = wideMarkerList.get(firstVisibleItem);
				selectInfo.showInfoWindow();
				
				actionMap.aniMap(selectInfo.getPosition() ,ActionMap.ZOOM_NOMAL);
			}
		}
	}

	@Override
	public void onScrollStateChanged(AbsListView view, int scrollState) {
		// TODO Auto-generated method stub

	}


	@Override
	public void OnReceive(Bundle data) {
		et.setText(data.getString(KEY_TAB_SELECTION));
		isReceived = true;
	}

}
