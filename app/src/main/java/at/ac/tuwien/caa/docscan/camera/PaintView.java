/*********************************************************************************
 *  DocScan is a Android app for document scanning.
 *
 *  Author:         Fabian Hollaus, Florian Kleber, Markus Diem
 *  Organization:   TU Wien, Computer Vision Lab
 *  Date created:   12. July 2016
 *
 *  This file is part of DocScan.
 *
 *  DocScan is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU Lesser General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  DocScan is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU Lesser General Public License
 *  along with DocScan.  If not, see <http://www.gnu.org/licenses/>.
 *********************************************************************************/

package at.ac.tuwien.caa.docscan.camera;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.PointF;
import android.graphics.PorterDuff;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

import java.util.ArrayList;
import java.util.Locale;

import at.ac.tuwien.caa.docscan.R;
import at.ac.tuwien.caa.docscan.camera.cv.CVResult;
import at.ac.tuwien.caa.docscan.camera.cv.DkPolyRect;
import at.ac.tuwien.caa.docscan.camera.cv.Patch;

/**
 * Class responsible for drawing the results of the page segmentation and focus measurement tasks.
 * The thread handling is based on this Android example:
 * <a href="https://github.com/Miserlou/Android-SDK-Samples/blob/master/LunarLander">Lunar Lander</a>
 */
public class PaintView extends SurfaceView implements SurfaceHolder.Callback {


    private static final String TAG = "PaintView";
    private DrawerThread mDrawerThread;
    private TaskTimer.TimerCallbacks mTimerCallbacks;

    // boolean indicating whether the values of the focus measurement should be drawn:
    private boolean mDrawFocusText;
    private boolean mDrawGuideLines = false;
    private boolean mDrawMovementIndicator = false;
    private boolean mDrawWaitingIndicator = false;
    private SurfaceHolder mHolder;
    private static Flicker mFlicker;
    private CVResult mCVResult;

    /**
     * Creates the PaintView, the timerCallback and the thread responsible for the drawing.
     * @param context of the Activity
     * @param attrs attributes of the activity. Note that we need to pass the attributes in order to
     *              find the view by its ID (findViewById) in the activity.
     */
    public PaintView(Context context, AttributeSet attrs) {

        super(context, attrs);

        // This is necessary to assure that the view is on top of the camera view. On some devices
        // like Galaxy Tab 10.1 this was not always the case.
        this.setZOrderMediaOverlay(true);

        SurfaceHolder holder = getHolder();
        holder.addCallback(this);
        // This is necessary to enable semi-transparent DrawView
        holder.setFormat(PixelFormat.TRANSLUCENT);
        mHolder = holder;

        mTimerCallbacks = (TaskTimer.TimerCallbacks) context;

        mDrawerThread = new DrawerThread(holder);

        mDrawFocusText = false;

        mFlicker = new Flicker();

    }

    /**
     * Method used for turning the drawing of the focus values on or off
     * @param drawText boolean
     */
    public void drawFocusText(boolean drawText) {

        mDrawFocusText = drawText;

    }

    /**
     * Returns if the focus values are drawn.
     * @return boolean
     */
    public boolean isFocusTextVisible() {

        return mDrawFocusText;

    }

    /**
     * Sets the CVResult. The draw method is only called if the CVResult notifies the PaintView that
     * it has been updated.
     * @param cvResult CVResult to which the PaintView is connected
     */
    public void setCVResult(CVResult cvResult) {

        mCVResult = cvResult;

    }

    /**
     * Called after the surface is created.
     * @param holder owner of the surface
     */
    @Override
    public void surfaceCreated(SurfaceHolder holder)
    {

        mHolder = holder;
        mDrawerThread.setRunning(true);

        if (mDrawerThread.getState() == Thread.State.NEW)
            mDrawerThread.start();

    }

    /**
     * Called after the surface is changed.
     * @param holder owner of the surface
     * @param format format
     * @param width width of the surface
     * @param height height of the surface
     */
    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {

        mHolder = holder;

    }

    /**
     * Called if the surface is destroyed. Shuts down the drawer thread.
     * @param holder the surface holder
     */
    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {

        // we have to tell thread to shut down & wait for it to finish, or else
        // it might touch the Surface after we return and explode
        boolean retry = true;
        mDrawerThread.setRunning(false);
        while (retry) {
            try {
                mDrawerThread.join();
                retry = false;
            } catch (InterruptedException e) {
                Log.d(TAG, e.toString());
            }
        }

    }

    /**
     * Called if the activity is resumed. Here the drawer thread is recreated if necessary.
     */
    public void resume() {



        if (mDrawerThread != null) {
            // It is necessary to call notify here, because otherwise there will be a deadlock in the
            // run method, which waits for the mCVResult object. The deadlock will arise after the
            // app is resumed (for example after an orientation change).
            forceDrawUpdate();

            if (mDrawerThread.getState() == Thread.State.TERMINATED) {

                mDrawerThread = new DrawerThread(mHolder);
                mDrawerThread.setRunning(true);
                mDrawerThread.start();
            }
            else
                mDrawerThread.setRunning(true);
        }


    }

    /**
     * Pauses the drawer thread.
     */
    public void pause() {

        if (mDrawerThread != null) {
            // It is necessary to call notify here, because otherwise there will be a deadlock in the
            // run method, which waits for the mCVResult object. The deadlock will arise after the
            // app is resumed (for example after an orientation change).
            forceDrawUpdate();
            mDrawerThread.setRunning(false);
        }

    }


    public void drawGuideLines(boolean drawGuideLines) {

        mDrawGuideLines = drawGuideLines;

    }

    public boolean areGuideLinesDrawn() {

        return mDrawGuideLines;

    }

    public void showFlicker() {

        mFlicker.mVisible = true;
        forceDrawUpdate();

    }

    public void drawMovementIndicator(boolean drawMovementIndicator) {

        mDrawMovementIndicator = drawMovementIndicator;
        forceDrawUpdate();

    }

    public void drawWaitingIndicator(boolean drawWaitingIndicator) {

        mDrawWaitingIndicator = drawWaitingIndicator;
        forceDrawUpdate();

    }

    private void forceDrawUpdate() {

        // This is necessary, since we do not want to wait for the next update from the mCVResult:
        synchronized (mCVResult) {
            mCVResult.notify();
        }

    }

    /**
     * Class responsible for the actual drawing.
     */
    class DrawerThread extends Thread {

        private static final String TAG = "DrawerThread";
        private SurfaceHolder mSurfaceHolder;
        private boolean mIsRunning;


        private Paint mTextPaint;
        private Paint mBGPaint;
        private Paint mSegmentationPaint;
        private Path mSegmentationPath;
        private Path mFocusPath;
        private Paint mGuidePaint;
        private Paint mMovementPaint;
        private Paint mWaitingFramePaint;

        private final float FOCUS_RECT_HEIGHT = getResources().getDimension(R.dimen.focus_height);
        private final float FOCUS_RECT_HALF_HEIGHT = FOCUS_RECT_HEIGHT / 2;
        private final float FOCUS_RECT_SHORT_LENGTH = getResources().getDimension(R.dimen.focus_short_length);
        private final float FOCUS_RECT_OFFSET = (float) (FOCUS_RECT_SHORT_LENGTH * 1.5);
        private static final String TEXT_FORMAT = "%.2f";

        private final int STATE_TEXT_BG_COLOR = getResources().getColor(R.color.hud_state_rect_color);
        private final int PAGE_RECT_COLOR = getResources().getColor(R.color.hud_page_rect_color);
        private final int FOCUS_SHARP_RECT_COLOR = getResources().getColor(R.color.hud_focus_sharp_rect_color);
        private final int FOCUS_UNSHARP_RECT_COLOR = getResources().getColor(R.color.hud_focus_unssharp_rect_color);
        private final int HELPER_LINE_COLOR = getResources().getColor(R.color.hud_helper_line_color);


        private Paint mFocusSharpRectPaint;
        private Paint mFocusUnsharpRectPaint;

        // Used for debug output:

        public DrawerThread(SurfaceHolder surfaceHolder) {


            mSurfaceHolder = surfaceHolder;

//            Initialize drawing stuff:

            // Used to print out measured focus:
            mTextPaint = new Paint();
            // Get device independent screen units:
            // Taken from here: http://stackoverflow.com/questions/3061930/how-to-set-unit-for-paint-settextsize
            int scaledFontSize = getResources().getDimensionPixelSize(R.dimen.draw_view_focus_font_size);
            mTextPaint.setTextSize(scaledFontSize);


            // Used to paint the page segmentation boundaries:
            mSegmentationPaint = new Paint();
            mSegmentationPaint = new Paint();
            mSegmentationPaint.setColor(PAGE_RECT_COLOR);
            mSegmentationPaint.setStyle(Paint.Style.STROKE);
            mSegmentationPaint.setStrokeWidth(getResources().getDimension(R.dimen.page_stroke_width));
            mSegmentationPath = new Path();


            mFocusSharpRectPaint = new Paint();
            mFocusSharpRectPaint.setStrokeWidth(getResources().getDimension(R.dimen.focus_stroke_width));

            mFocusSharpRectPaint.setStyle(Paint.Style.STROKE);
            mFocusSharpRectPaint.setColor(FOCUS_SHARP_RECT_COLOR);

            mFocusUnsharpRectPaint = new Paint();
            mFocusUnsharpRectPaint.setStrokeWidth(getResources().getDimension(R.dimen.focus_stroke_width));
            mFocusUnsharpRectPaint.setStyle(Paint.Style.STROKE);
            mFocusUnsharpRectPaint.setColor(FOCUS_UNSHARP_RECT_COLOR);
            mFocusPath = new Path();

            mGuidePaint = new Paint();
            mGuidePaint.setStrokeWidth(2);
            mGuidePaint.setStyle(Paint.Style.STROKE);
            mGuidePaint.setColor(HELPER_LINE_COLOR);

            mBGPaint = new Paint();
            mBGPaint.setColor(STATE_TEXT_BG_COLOR);

            mMovementPaint = new Paint();
            mMovementPaint.setStyle(Paint.Style.FILL);
            mMovementPaint.setColor(getResources().getColor(R.color.movement_color));

            mWaitingFramePaint = new Paint();
            mWaitingFramePaint.setStyle(Paint.Style.FILL);
            mWaitingFramePaint.setColor(getResources().getColor(R.color.waiting_frame_color));

        }



        /**
         * Continuous looping method used for waiting for updates of the CVResult object. The draw
         * method is only called after the CVResult object is updated. Note that this saves a lot of
         * CPU usage.
         * @see <a href="https://developer.android.com/training/custom-views/optimizing-view.html">Optimizing the View</a>
         */
        @Override
        public void run() {

            while (mIsRunning) {

                Canvas canvas = null;

                if (mFlicker.mVisible)
                    mFlicker.draw();

                else {

                    // This wait is used to assure that the drawing function is just called after an update
                    // from the native package:
                    synchronized (mCVResult) {

                        try {
                            mCVResult.wait();
                        } catch (InterruptedException e) {
                            Log.d(TAG, e.toString());
                        }

                        if (mFlicker.mVisible)
                            continue;

                        try {
                            canvas = mSurfaceHolder.lockCanvas();
                            draw(canvas);
                        } finally {
                            // do this in a finally so that if an exception is thrown
                            // during the above, we don't leave the Surface in an
                            // inconsistent state
                            if (canvas != null) {
                                mSurfaceHolder.unlockCanvasAndPost(canvas);
                            }
                        }

                    }
                }


            }
        }

        /**
         * Used for pausing and resuming the drawer thread.
         * @param b boolean indicating if the thread is running
         */
        public void setRunning(boolean b) {

            synchronized (mSurfaceHolder) {

                mIsRunning = b;

            }

        }



        /**
         * Method used for the drawing of the CV result.
         * @param canvas canvas
         */
        private void draw(Canvas canvas) {

            if (canvas == null) {
                return;
            }

//            if (CameraActivity.isDebugViewEnabled())
//                mTimerCallbacks.onTimerStarted(DRAW_VIEW);

//            Clear the screen from previous drawings:
            canvas.drawColor(Color.BLUE, PorterDuff.Mode.CLEAR);

            if (mDrawMovementIndicator) {
                Rect rect = new Rect(0,0, getWidth(), getHeight());
                canvas.drawRect(rect, mMovementPaint);
            }

            if (mDrawWaitingIndicator) {
                Rect rect = new Rect(0,0, getWidth(), getHeight());
                canvas.drawRect(rect, mWaitingFramePaint);
            }

            if (mCVResult != null) {

                // Focus measure:
                if (mCVResult.getPatches() != null)
                    drawFocusMeasure(canvas);

                // Page segmentation:
                if (mCVResult.getDKPolyRects() != null)
                    drawPageSegmentation(canvas);

//                drawSavingAnimation(canvas);

                // Output the document state:
//                drawDocumentState(canvas);

            }

//            if (CameraActivity.isDebugViewEnabled())
//                mTimerCallbacks.onTimerStopped(DRAW_VIEW);

        }




        /**
         * Draws the output of the page segmentation task.
         * @param canvas canvas
         */
        private void drawPageSegmentation(Canvas canvas) {

            for (DkPolyRect dkPolyRect : mCVResult.getDKPolyRects()) {

                mSegmentationPath.reset();
                ArrayList<PointF> screenPoints = dkPolyRect.getScreenPoints();
                boolean isStartSet = false;

//                int idx = 0; // used for the drawing of the corner numbers
                for (PointF point : screenPoints) {

                    if (!isStartSet) {
                        mSegmentationPath.moveTo(point.x, point.y);
                        isStartSet = true;
                    } else
                        mSegmentationPath.lineTo(point.x, point.y);

                    if (mDrawGuideLines) {
                        canvas.drawLine(0, point.y, getWidth(), point.y, mGuidePaint);
                        canvas.drawLine(point.x, 0, point.x, getHeight(), mGuidePaint);
                    }

//                    Draw the corner number:
//                    String cornerNum = String.format(Locale.ENGLISH, "%d", idx);
//                    canvas.drawText(cornerNum, point.x, point.y, mTextPaint);
//                    idx++;
                }

                mSegmentationPath.close();
                canvas.drawPath(mSegmentationPath, mSegmentationPaint);


            }

        }

        /**
         * Draws the output of the focus measurement task.
         * @param canvas canvas
         */
        private void drawFocusMeasure(Canvas canvas) {

            Rect textRect = new Rect();
            float textWidth = -1;

            mFocusPath.reset();

            for (Patch patch : mCVResult.getPatches()) {

                String fValue = String.format(Locale.ENGLISH, TEXT_FORMAT, patch.getFM());

                if (patch.getIsForeGround()) {

                    if (patch.getIsSharp()) {

                        if (mDrawFocusText)
                            mTextPaint.setColor(FOCUS_SHARP_RECT_COLOR);

                        drawFocusRect(canvas, mFocusSharpRectPaint, patch.getDrawViewPX(), patch.getDrawViewPY());

                    }
                    else {

                        if (mDrawFocusText)
                            mTextPaint.setColor(FOCUS_UNSHARP_RECT_COLOR);

                        drawFocusRect(canvas, mFocusUnsharpRectPaint, patch.getDrawViewPX(), patch.getDrawViewPY());


                    }


                    if (mDrawFocusText) {

                        // Get the size of the text in screen units so that we can center the text:
                        // (Do this just for the first iteration.)
                        if (textWidth == -1) {
                            mTextPaint.getTextBounds(fValue, 0, fValue.length(), textRect);
                            textWidth = (float) textRect.width() / 2;
                        }


                        canvas.drawText(fValue, patch.getDrawViewPX() - textWidth,
                                patch.getDrawViewPY(), mTextPaint);

                    }
                }
            }
        }

        private void drawFocusRect(Canvas canvas, Paint paint, float x, float y) {

            mFocusPath.reset();

            mFocusPath.moveTo(x - FOCUS_RECT_OFFSET + FOCUS_RECT_SHORT_LENGTH, y - FOCUS_RECT_HALF_HEIGHT);
            mFocusPath.lineTo(x - FOCUS_RECT_OFFSET, y - FOCUS_RECT_HALF_HEIGHT);
            mFocusPath.lineTo(x - FOCUS_RECT_OFFSET, y + FOCUS_RECT_HALF_HEIGHT);
            mFocusPath.lineTo(x - FOCUS_RECT_OFFSET + FOCUS_RECT_SHORT_LENGTH, y + FOCUS_RECT_HALF_HEIGHT);

            mFocusPath.moveTo(x + FOCUS_RECT_OFFSET - FOCUS_RECT_SHORT_LENGTH, y - FOCUS_RECT_HALF_HEIGHT);
            mFocusPath.lineTo(x + FOCUS_RECT_OFFSET, y - FOCUS_RECT_HALF_HEIGHT);
            mFocusPath.lineTo(x + FOCUS_RECT_OFFSET, y + FOCUS_RECT_HALF_HEIGHT);
            mFocusPath.lineTo(x + FOCUS_RECT_OFFSET - FOCUS_RECT_SHORT_LENGTH, y + FOCUS_RECT_HALF_HEIGHT);

            canvas.drawPath(mFocusPath, paint);

        }
    }

    /**
     * This class is used when a picture is taken.
     */
    private class Flicker {

        private Paint mFlickerPaint;
        private boolean mVisible;

        private Flicker() {

            mFlickerPaint = new Paint();
            mFlickerPaint.setStyle(Paint.Style.FILL);
            mFlickerPaint.setColor(getResources().getColor(R.color.flicker_color));
            mVisible = false;

        }

        private void draw() {

            Canvas canvas = mHolder.lockCanvas();

            if (canvas == null)
                return;

            Rect rect = new Rect(0,0, getWidth(), getHeight());
            canvas.drawRect(rect, mFlickerPaint);

            mHolder.unlockCanvasAndPost(canvas);

            // After the painting wait some time:
            long startTime = System.currentTimeMillis();
            long currentTime = -1;

            while(currentTime - startTime < getResources().getInteger(R.integer.flicker_time))
                currentTime = System.currentTimeMillis();

            // Remove the painting:
            canvas = mHolder.lockCanvas();
            canvas.drawColor(Color.BLUE, PorterDuff.Mode.CLEAR);

            mHolder.unlockCanvasAndPost(canvas);

            mVisible = false;

        }

    }

 }
