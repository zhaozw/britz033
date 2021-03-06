package internet;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;

import org.xmlpull.v1.XmlPullParser;

import android.content.Context;
import android.os.AsyncTask;
import android.util.Log;

/*
 * BackHttpConnection, BackXmlParser,BusStationParsing  클래스로
 * 각각 인터넷 연결, xml 파싱, 파싱된 데이터 처리를 담당한다. 
 * 각자가 커스텀 익셉션을 소유하고 있으며 그 익셉션의 메세지를 추출하여 그대로 TextView에서 사용
 * 결과값과 에러메세지는 ResponseTask proxy.onFinishTask 인터페이스를 통해 
 * 테스크가 끝난후 전달되며 이때 에러메세지가 null 일 경우는 에러메세지 대신 일반적 데이터를 표시한다.
 */
public class ConnectTask extends AsyncTask<String, Integer, ArrayList<BusInfoNet>> {

	private static final String TAG = "ConnectTask";
	
	public static final String BUS_URL = "http://businfo.daegu.go.kr/ba/arrbus/arrbus.do?act=arrbus&winc_id=";

	private Context context;
	private String stationNumber;
	private String errorMessage = null;
	public ResponseTask proxy = null;

	public ConnectTask(Context context, String stationNumber) {
		this.context = context;
		this.stationNumber = stationNumber;
	}

	@Override
	protected ArrayList<BusInfoNet> doInBackground(String... s) {
		ArrayList<BusInfoNet> busInfoList = new ArrayList<BusInfoNet>();

		InputStream is = null;
		try {
			if (!stationNumber.equals("0")) {
				is = new BackHttpConnection(context, BUS_URL + stationNumber).getInputStream();
				XmlPullParser parser = new BackXmlParser(is, "euc-kr").getParser();
				BusStationParsing parsingWork = new BusStationParsing(parser, busInfoList);
			} else {
				errorMessage = "0번 정류장은 전광판정보가 제공되지 않습니다";
			}
		} catch (Exception e) {
			errorMessage = e.getMessage();
		} finally {
			if (is != null) {
				try {
					is.close();
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
		}
		return busInfoList;
	}

	@Override
	protected void onPostExecute(ArrayList<BusInfoNet> result) {
		super.onPostExecute(result);

		if (result == null) {
			Log.d(TAG, "결과값이 null");
		}
		
		proxy.onTaskFinish(result, errorMessage);
		// wait.dismiss();

	}

}
