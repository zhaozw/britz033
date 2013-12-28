package util;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.channels.FileChannel;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.media.ExifInterface;
import android.net.Uri;

public class ImageUtil {
	
	private File mTargetFile;
	private Context mContext;
	
	public ImageUtil(File target, Context context){
		mTargetFile = target;
		mContext = context;
	}
	
	public void startCamera(){
		correctCameraOrientation(mTargetFile);
	}
	
	public void startGallery(Uri galleryUri){
		copyUriToFile(galleryUri, mTargetFile);
		Bitmap bitmap = loadImageWithSampleSize(mTargetFile);
		saveBitmapToFile(bitmap);
	}
	

	/** 이미지 사이즈 수정 후, 카메라 rotation 정보가 있으면 회전 보정함. */
	private void correctCameraOrientation(File imgFile) {
		Bitmap bitmap = loadImageWithSampleSize(imgFile); // 크기보정
		
		// 회전보정
		try {
			ExifInterface exif = new ExifInterface(imgFile.getAbsolutePath());
			int exifOrientation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL);
			int exifRotateDegree = exifOrientationToDegrees(exifOrientation);
			bitmap = rotateImage(bitmap, exifRotateDegree);
			saveBitmapToFile(bitmap);	// 세이브
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	/** 원하는 크기의 이미지로 options 설정. */
	private Bitmap loadImageWithSampleSize(File file) {
		BitmapFactory.Options options = new BitmapFactory.Options();
		options.inJustDecodeBounds = true;
		BitmapFactory.decodeFile(file.getAbsolutePath(), options);
		int width = options.outWidth;
		int height = options.outHeight;
		int longSide = Math.max(width, height);
		int sampleSize = 1;
		if (longSide > mImageSizeBoundary) {
			sampleSize = longSide / mImageSizeBoundary;
		}
		options.inJustDecodeBounds = false;
//		options.inSampleSize = sampleSize;
		options.inSampleSize = 9;
		options.inPurgeable = true;
		options.inDither = false;

		Bitmap bitmap = BitmapFactory.decodeFile(file.getAbsolutePath(), options);
		return bitmap;
	}
	
	/**
	 * EXIF정보를 회전각도로 변환하는 메서드
	 * 
	 * @param exifOrientation
	 *            EXIF 회전각
	 * @return 실제 각도
	 */
	private int exifOrientationToDegrees(int exifOrientation) {
		if (exifOrientation == ExifInterface.ORIENTATION_ROTATE_90) {
			return 90;
		} else if (exifOrientation == ExifInterface.ORIENTATION_ROTATE_180) {
			return 180;
		} else if (exifOrientation == ExifInterface.ORIENTATION_ROTATE_270) {
			return 270;
		}
		return 0;
	}
	
	private Bitmap rotateImage(Bitmap bitmap, int degrees) {
		if (degrees != 0 && bitmap != null) {
			Matrix m = new Matrix();
			m.setRotate(degrees, (float) bitmap.getWidth() / 2, (float) bitmap.getHeight() / 2);
			try {
				Bitmap converted = Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), m, true);
				if (bitmap != converted) {
					bitmap.recycle();
					bitmap = converted;
				}
			} catch (OutOfMemoryError ex) {
			}
		}
		return bitmap;
	}
	
	private void saveBitmapToFile(Bitmap bitmap) {
		File target = mTargetFile;
		try {
			FileOutputStream fos = new FileOutputStream(target, false);
			bitmap.compress(Bitmap.CompressFormat.PNG, 100, fos);
			fos.close();
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	/**
	 * 사용할 이미지의 최대 크기 설정. 가로, 세로 지정한 크기보다 작은 사이즈로 이미지 크기를 조절함. default size :
	 * 500
	 * 
	 * @param sizePixel
	 *            기본 500
	 */
	private int mImageSizeBoundary = 500;

	public void setImageSizeBoundary(int sizePixel) {
		mImageSizeBoundary = sizePixel;
	}
	
	private void copyUriToFile(Uri srcUri, File target) {
		FileInputStream inputStream = null;
		FileOutputStream outputStream = null;
		FileChannel fcin = null;
		FileChannel fcout = null;
		try {
			// 스트림 생성
			inputStream = (FileInputStream) mContext.getContentResolver().openInputStream(srcUri);
			outputStream = new FileOutputStream(target);

			// 채널 생성
			fcin = inputStream.getChannel();
			fcout = outputStream.getChannel();

			// 채널을 통한 스트림 전송
			long size = fcin.size();
			fcin.transferTo(0, size, fcout);
		} catch (Exception e) {
			e.printStackTrace();
		} finally {
			try {
				fcout.close();
			} catch (IOException ioe) {
			}
			try {
				fcin.close();
			} catch (IOException ioe) {
			}
			try {
				outputStream.close();
			} catch (IOException ioe) {
			}
			try {
				inputStream.close();
			} catch (IOException ioe) {
			}
		}
	}
}
