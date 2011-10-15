package com.write.Quill;

import java.util.List;
import java.util.LinkedList;
import java.util.ListIterator;

import com.write.Quill.Stroke.PenType;

import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Path;
import android.util.FloatMath;
import android.util.Log;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Toast;
import android.os.AsyncTask;

public class HandwriterView extends View {
	private static final String TAG = "Handwrite";

	private Bitmap bitmap;
	private Canvas canvas;
	private Toast toast;
	private final Rect mRect = new Rect();
	private final RectF mRectF = new RectF();
	private final Paint pen;
	private int penID = -1;
	private int fingerId1 = -1;
	private int fingerId2 = -1;
	private float oldPressure, newPressure; 
	private float oldX, oldY, newX, newY;  // main pointer
	private float oldX2, oldY2, newX2, newY2;  // for 2nd finger
	private long oldT, newT;
	
    private int N = 0;
	private static final int Nmax = 1024;
	private float[] position_x = new float[Nmax];
	private float[] position_y = new float[Nmax];
	private float[] pressure = new float[Nmax];

	// persistent data
	Page page;
	
	// preferences
	protected int pen_thickness = 2;
	protected PenType pen_type = PenType.FOUNTAINPEN;
	protected int pen_color = Color.BLACK;
	protected boolean only_pen_input = true;
	
	public void set_pen_type(PenType t) {
		pen_type = t;
	}

	public void set_pen_color(int c) {
		pen_color = c;
		pen.setARGB(Color.alpha(c), Color.red(c), Color.green(c), Color.blue(c));
	}
	
	public void set_pen_thickness(int thickness) {
		pen_thickness = thickness;
	}
	
	public void set_page_paper_type(Page.PaperType paper_type) {
		page.set_paper_type(paper_type);
		page.draw(canvas);
		invalidate();
	}

	public void set_page_aspect_ratio(float aspect_ratio) {
		page.set_aspect_ratio(aspect_ratio);
		set_page_and_zoom_out(page);
		invalidate();
	}

	public HandwriterView(Context c) {
		super(c);
		setFocusable(true);
		pen = new Paint();
		pen.setAntiAlias(true);
		pen.setARGB(0xff, 0, 0, 0);	
		pen.setStrokeCap(Paint.Cap.ROUND);
	}

	public void set_page_and_zoom_out(Page new_page) {
		if (new_page == null) return;
		page = new_page;
		if (canvas == null) return;
		// if (getResources().getConfiguration().orientation == Configuration.ORIENTATION_PORTRAIT) 
		float H = canvas.getHeight();
		float W = canvas.getWidth();
		float dimension = Math.min(H, W/page.aspect_ratio);
		float h = dimension; 
		float w = dimension*page.aspect_ratio;
		if (h<H)
			page.set_transform(0, (H-h)/2, dimension);
		else if (w<W)
			page.set_transform((W-w)/2, 0, dimension);
		else
			page.set_transform(0, 0, dimension);
		Log.v(TAG, "set_page at scale "+page.scale+" canvas w="+W+" h="+H);
		page.draw(canvas);
		invalidate();
	}
	
	public void clear() {
		if (canvas == null || page == null) return;		
		page.strokes.clear();	
		page.draw(canvas);	
		invalidate();
	}
	
	protected void add_strokes(Object data) {
		assert data instanceof LinkedList<?>: "unknown data";
		LinkedList<Stroke> new_strokes = (LinkedList<Stroke>)data;
		page.strokes.addAll(new_strokes);
	}
	
	@Override protected void onSizeChanged(int w, int h, int oldw,
			int oldh) {
		int curW = bitmap != null ? bitmap.getWidth() : 0;
		int curH = bitmap != null ? bitmap.getHeight() : 0;
		if (curW >= w && curH >= h) {
			return;
		}
		if (curW < w) curW = w;
		if (curH < h) curH = h;

		Bitmap newBitmap = Bitmap.createBitmap(curW, curH,
				Bitmap.Config.RGB_565);
		Canvas newCanvas = new Canvas();
		newCanvas.setBitmap(newBitmap);
		if (bitmap != null) {
			newCanvas.drawBitmap(bitmap, 0, 0, null);
		}
		bitmap = newBitmap;
		canvas = newCanvas;
		set_page_and_zoom_out(page);
	}

	private float pinch_zoom_scale_factor() {
		float dx, dy;
		dx = oldX-oldX2;
		dy = oldY-oldY2;
		float old_distance = FloatMath.sqrt(dx*dx + dy*dy);
		if (old_distance < 10) {
			// Log.d("TAG", "old_distance too small "+old_distance);
			return 1;
		}
		dx = newX-newX2;
		dy = newY-newY2;
		float new_distance = FloatMath.sqrt(dx*dx + dy*dy);
		float scale = new_distance / old_distance;
		if (scale < 0.1f || scale > 10f) {
			// Log.d("TAG", "ratio out of bounds "+new_distance);
			return 1;
		}
		return scale;
	}
	
	public float get_scaled_pen_thickness() {
		return Stroke.get_scaled_pen_thickness(page.scale, pen_thickness);
	}
	
	@Override protected void onDraw(Canvas canvas) {
		if (bitmap == null) return;
		if (pen_type == Stroke.PenType.MOVE && fingerId2 != -1) {
			// pinch-to-zoom preview by scaling bitmap
			canvas.drawARGB(0xff, 0xaa, 0xaa, 0xaa);
			float W = canvas.getWidth();
			float H = canvas.getHeight();
			float scale = pinch_zoom_scale_factor();
			float x0 = (oldX + oldX2)/2;
			float y0 = (oldY + oldY2)/2;
			float x1 = (newX + newX2)/2;
			float y1 = (newY + newY2)/2;
			mRectF.set(-x0*scale+x1, -y0*scale+y1, (-x0+W)*scale+x1, (-y0+H)*scale+y1);
			mRect.set(0, 0, canvas.getWidth(), canvas.getHeight());
			canvas.drawBitmap(bitmap, mRect, mRectF, (Paint)null);
		} else if (pen_type == Stroke.PenType.MOVE && fingerId1 != -1) {
			// move preview by translating bitmap
			canvas.drawARGB(0xff, 0xaa, 0xaa, 0xaa);
			float x = newX-oldX;
			float y = newY-oldY; 
			canvas.drawBitmap(bitmap, x, y, null);
		} else
			canvas.drawBitmap(bitmap, 0, 0, null);
	}

	@Override public boolean onTouchEvent(MotionEvent event) {
//		InputDevice dev = event.getDevice();
//		Log.v(TAG, "Touch: "+dev.getId()+" "+dev.getName()+" "+dev.getKeyboardType()+" "+dev.getSources()+" ");
//		Log.v(TAG, "Touch: "+event.getDevice().getName()
//				+" action="+event.getActionMasked()
//				+" pressure="+event.getPressure()
//				+" fat="+event.getTouchMajor()
//				+" penID="+penID+" ID="+event.getPointerId(0)+" N="+N);
		switch (pen_type) {
		case FOUNTAINPEN:
		case PENCIL:	
			return touch_handler_pen(event);
		case MOVE:
			return touch_handler_move_zoom(event);
		case ERASER:
			return touch_handler_eraser(event);
		}
		return false;
	}
		
	private boolean touch_handler_eraser(MotionEvent event) {
		int action = event.getActionMasked();
		if (action == MotionEvent.ACTION_MOVE) {
			if (penID == -1) return true;
			int idx = event.findPointerIndex(penID);
			if (idx == -1) return true;
			newX = event.getX(idx);
			newY = event.getY(idx);
			mRectF.set(oldX, oldY, newX, newY);
			mRectF.sort();
			mRectF.inset(-15, -15);
			boolean erased = page.erase_strokes_in(mRectF, canvas);
			if (erased)
				invalidate();
			oldX = newX;
			oldY = newY;
			return true;
		} else if (action == MotionEvent.ACTION_DOWN) {  // start move
			penID = event.getPointerId(0);
			oldX = newX = event.getX();
			oldY = newY = event.getY();
			return true;
		} else if (action == MotionEvent.ACTION_UP) {  
			penID = -1;
		}
		return false;
	}
	
	
	private boolean touch_handler_move_zoom(MotionEvent event) {
		int action = event.getActionMasked();
		if (action == MotionEvent.ACTION_MOVE) {
			if (fingerId1 == -1) return true;
			if (fingerId2 == -1) {  // move
				int idx = event.findPointerIndex(fingerId1);
				if (idx == -1) return true;
				newX = event.getX(idx);
				newY = event.getY(idx);
			} else { // pinch-to-zoom
				int idx1 = event.findPointerIndex(fingerId1);
				int idx2 = event.findPointerIndex(fingerId2);
				if (idx1 == -1 || idx2 == -1) return true;
				newX = event.getX(idx1);
				newY = event.getY(idx1);
				newX2 = event.getX(idx2);
				newY2 = event.getY(idx2);
			}
			invalidate();
			return true;
		}		
		else if (action == MotionEvent.ACTION_DOWN) {  // start move
			oldX = newX = event.getX();
			oldY = newY = event.getY();
			fingerId1 = event.getPointerId(0); 
			fingerId2 = -1;
			// Log.v(TAG, "ACTION_DOWN "+fingerId1);
			return true;
		}
		else if (action == MotionEvent.ACTION_UP) {  // stop move
			if (fingerId1 == -1) return true;  // ignore after pinch-to-zoom
			if (fingerId2 != -1) { // undelivered ACTION_POINTER_UP
				fingerId1 = fingerId2 = -1;
				invalidate();
				return true;		
			}
			newX = event.getX();
			newY = event.getY();
			float dx = newX-oldX;
			float dy = newY-oldY; 
			// Log.v(TAG, "ACTION_UP "+fingerId1+" dx="+dx+", dy="+dy);
			page.set_transform(page.offset_x+dx, page.offset_y+dy, page.scale, canvas);
			page.draw(canvas);
			invalidate();
			fingerId1 = fingerId2 = -1;
			return true;
		}
		else if (action == MotionEvent.ACTION_POINTER_DOWN) {  // start pinch
			if (fingerId1 == -1) return true; // ignore after pinch-to-zoom finished
			if (fingerId2 != -1) return true; // ignore more than 2 fingers
			int idx2 = event.getActionIndex();
			oldX2 = newX2 = event.getX(idx2);
			oldY2 = newY2 = event.getY(idx2);
			fingerId2 = event.getPointerId(idx2);
			// Log.v(TAG, "ACTION_POINTER_DOWN "+fingerId2+" + "+fingerId1);
		}
		else if (action == MotionEvent.ACTION_POINTER_UP) {  // stop pinch
			if (fingerId1 == -1) return true; // ignore after pinch-to-zoom finished
			int idx = event.getActionIndex();
			int Id = event.getPointerId(idx);
			if (fingerId1 != Id && fingerId2 != Id) // third finger up?
				return true;
			// Log.v(TAG, "ACTION_POINTER_UP "+fingerId2+" + "+fingerId1);
			// compute scale factor
			float scale = pinch_zoom_scale_factor();
			float new_page_scale = page.scale * scale;
			// clamp scale factor
			float W = canvas.getWidth();
			float H = canvas.getHeight();
			float max_WH = Math.max(W, H);
			float min_WH = Math.min(W, H);
			new_page_scale = Math.min(new_page_scale, 5*max_WH);
			new_page_scale = Math.max(new_page_scale, 0.4f*min_WH);
			scale = new_page_scale / page.scale;
			// compute offset
			float x0 = (oldX + oldX2)/2;
			float y0 = (oldY + oldY2)/2;
			float x1 = (newX + newX2)/2;
			float y1 = (newY + newY2)/2;
			float new_offset_x = page.offset_x*scale-x0*scale+x1;
			float new_offset_y = page.offset_y*scale-y0*scale+y1;
			// perform pinch-to-zoom here
			page.set_transform(new_offset_x, new_offset_y, new_page_scale, canvas);
			page.draw(canvas);
			invalidate();
			fingerId1 = fingerId2 = -1;
		}
		else if (action == MotionEvent.ACTION_CANCEL) {
			fingerId1 = fingerId2 = -1;
			return true;
		}
		return false;
	}
	
	private boolean touch_handler_pen(MotionEvent event) {
		int action = event.getActionMasked();
		if (action == MotionEvent.ACTION_MOVE) {
			if (penID == -1 || N == 0) return true;
			int penIdx = event.findPointerIndex(penID);
			if (penIdx == -1) return true;
			
			oldT = newT;
			newT = System.currentTimeMillis();
			// Log.v(TAG, "ACTION_MOVE index="+pen+" pointerID="+penID);
			oldX = newX;
			oldY = newY;
			oldPressure = newPressure;
			newX = event.getX(penIdx);
			newY = event.getY(penIdx);
			newPressure = event.getPressure(penIdx);
			if (newT-oldT > 300) { // sometimes ACTION_UP is lost, why?
				Log.v(TAG, "Timeout in ACTION_MOVE, "+(newT-oldT));
				oldX = newX; oldY = newY;
				save_stroke();
				position_x[0] = newX;
				position_y[0] = newY;
				pressure[0] = newPressure;
				N = 1;
			}
			drawOutline();
			
			int n = event.getHistorySize();
			if (N+n+1 >= Nmax) save_stroke();
			for (int i = 0; i < n; i++) {
				position_x[N+i] = event.getHistoricalX(penIdx, i);
				position_y[N+i] = event.getHistoricalY(penIdx, i);
				pressure[N+i] = event.getHistoricalPressure(penIdx, i);
			}
			position_x[N+n] = newX;
			position_y[N+n] = newY;
			pressure[N+n] = newPressure;
			N = N+n+1;
			return true;
		}		
		else if (action == MotionEvent.ACTION_DOWN) {
			if (penID != -1) return true;
			if (only_pen_input && event.getTouchMajor() != 0.0f)
					return true;   // eat non-pen events
			// Log.v(TAG, "ACTION_DOWN");
			if (page.is_readonly) {
				toast_is_readonly();
				return true;
			}
			position_x[0] = newX = event.getX();
			position_y[0] = newY = event.getY();
			pressure[0] = newPressure = event.getPressure();
			newT = System.currentTimeMillis();
			N = 1;
			penID = event.getPointerId(0);
			pen.setStrokeWidth(get_scaled_pen_thickness());
			return true;
		}
		else if (action == MotionEvent.ACTION_UP) {
			if (event.getPointerId(0) != penID) return true;
			// Log.v(TAG, "ACTION_UP: Got "+N+" points.");
			save_stroke();
			N = 0;
			penID = -1;
			return true;
		}
		else if (action == MotionEvent.ACTION_CANCEL) {
			// e.g. you start with finger and use pen
			if (event.getPointerId(0) != penID) return true;
			// Log.v(TAG, "ACTION_CANCEL");
			N = 0;
			penID = -1;
			page.draw(canvas);
			invalidate();
			return true;
		}
		return false;
	}

	private void toast_is_readonly() {
		String s = "Page is readonly";
	   	if (toast == null)
        	toast = Toast.makeText(getContext(), s, Toast.LENGTH_SHORT);
    	else {
    		toast.setText(s);
    	}
	   	toast.show();
	}
	
	private void save_stroke() {
		if (N==0) return;
		Stroke s = new Stroke(position_x, position_y, pressure, 0, N);
		s.set_pen(pen_type, pen_thickness, pen_color);
		if (page != null) {
			page.add_stroke(s);
			page.draw(canvas, s.get_bounding_box());
		}
		N = 0;
		s.get_bounding_box().round(mRect);
		int extra = -(int)(get_scaled_pen_thickness()/2) - 1;
		mRect.inset(extra, extra);
		invalidate(mRect);
	}
	
	
	private void drawOutline() {
		if (pen_type==PenType.FOUNTAINPEN) {
			float scaled_pen_thickness = get_scaled_pen_thickness() * (oldPressure+newPressure)/2f;
			pen.setStrokeWidth(scaled_pen_thickness);
		}
		canvas.drawLine(oldX, oldY, newX, newY, pen);
		mRect.set((int)oldX, (int)oldY, (int)newX, (int)newY);
		mRect.sort();
		int extra = -(int)(pen.getStrokeWidth()/2) - 1;
		mRect.inset(extra, extra);
		invalidate(mRect);
	}
}
