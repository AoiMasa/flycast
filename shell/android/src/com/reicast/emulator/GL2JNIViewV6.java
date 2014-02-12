package com.reicast.emulator;


import javax.microedition.khronos.egl.EGL10;
import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.egl.EGLContext;
import javax.microedition.khronos.egl.EGLDisplay;
import javax.microedition.khronos.opengles.GL10;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.graphics.PixelFormat;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.opengl.EGL14;
import android.opengl.EGLExt;
import android.opengl.GLSurfaceView;
import android.os.Build;
import android.os.Vibrator;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.ScaleGestureDetector.SimpleOnScaleGestureListener;
import android.view.View;


/**
 * A simple GLSurfaceView sub-class that demonstrate how to perform
 * OpenGL ES 2.0 rendering into a GL Surface. Note the following important
 * details:
 *
 * - The class must use a custom context factory to enable 2.0 rendering.
 *   See ContextFactory class definition below.
 *
 * - The class must use a custom EGLConfigChooser to be able to select
 *   an EGLConfig that supports 2.0. This is done by providing a config
 *   specification to eglChooseConfig() that has the attribute
 *   EGL14.ELG_RENDERABLE_TYPE containing the EGL_OPENGL_ES2_BIT flag
 *   set. See ConfigChooser class definition below.
 *
 * - The class must select the surface's format, then choose an EGLConfig
 *   that matches it exactly (with regards to red/green/blue/alpha channels
 *   bit depths). Failure to do so would result in an EGL_BAD_MATCH error.
 */

class GL2JNIViewV6 extends GLSurfaceView
{
  private static String fileName;
  //private AudioThread audioThread;  
  private EmuThread ethd = new EmuThread();

  private static final boolean DEBUG           = false;
  
  Vibrator vib;

  private boolean editVjoyMode = false;
  private int selectedVjoyElement = -1;
  private ScaleGestureDetector scaleGestureDetector;
  
  private static float[][] vjoy_d_custom;

  private static final float[][] vjoy = new float[][]
		  { 
		    new float[] { 24+0,     24+64,   64,64, VJoy.key_CONT_DPAD_LEFT, 0},
		    new float[] { 24+64,    24+0,    64,64, VJoy.key_CONT_DPAD_UP, 0},
		    new float[] { 24+128,   24+64,   64,64, VJoy.key_CONT_DPAD_RIGHT, 0},
		    new float[] { 24+64,    24+128,  64,64, VJoy.key_CONT_DPAD_DOWN, 0},

		    new float[] { 440+0,    280+64,  64,64, VJoy.key_CONT_X, 0},
		    new float[] { 440+64,   280+0,   64,64, VJoy.key_CONT_Y, 0},
		    new float[] { 440+128,  280+64,  64,64, VJoy.key_CONT_B, 0},
		    new float[] { 440+64,   280+128, 64,64, VJoy.key_CONT_A, 0},

		    new float[] { 320-32,   360+32,  64,64, VJoy.key_CONT_START, 0},
		    
		    new float[] { 440, 200,  90,64, -1, 0},
		    new float[] { 542, 200,  90,64, -2, 0},
		    
		    new float[] { 0,   128+224,  128,128, -3, 0},
		    new float[] { 96, 320,  32,32, -4, 0},
		    
		    
		  };
  
  Renderer rend;

  private boolean touchVibrationEnabled;
  Context context;

  public static float[][] readCustomVjoyValues(Context context) {
       SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);

       return new float[][]
       {
        // x-shift, y-shift, sizing-factor
        new float[] { prefs.getFloat("touch_x_shift_dpad", 0), prefs.getFloat("touch_y_shift_dpad", 0), prefs.getFloat("touch_scale_dpad", 1) }, // DPAD
        new float[] { prefs.getFloat("touch_x_shift_buttons", 0), prefs.getFloat("touch_y_shift_buttons", 0), prefs.getFloat("touch_scale_buttons", 1) }, // X, Y, B, A Buttons
        new float[] { prefs.getFloat("touch_x_shift_start", 0), prefs.getFloat("touch_y_shift_start", 0), prefs.getFloat("touch_scale_start", 1) }, // Start
        new float[] { prefs.getFloat("touch_x_shift_left_trigger", 0), prefs.getFloat("touch_y_shift_left_trigger", 0), prefs.getFloat("touch_scale_left_trigger", 1) }, // Left Trigger
        new float[] { prefs.getFloat("touch_x_shift_right_trigger", 0), prefs.getFloat("touch_y_shift_right_trigger", 0), prefs.getFloat("touch_scale_right_trigger", 1) }, // Right Trigger
        new float[] { prefs.getFloat("touch_x_shift_analog", 0), prefs.getFloat("touch_y_shift_analog", 0), prefs.getFloat("touch_scale_analog", 1) } // Analog Stick
       };
  }

  public void resetCustomVjoyValues() {
       SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);

       prefs.edit().remove("touch_x_shift_dpad").commit();
       prefs.edit().remove("touch_y_shift_dpad").commit();
       prefs.edit().remove("touch_scale_dpad").commit();

       prefs.edit().remove("touch_x_shift_buttons").commit();
       prefs.edit().remove("touch_y_shift_buttons").commit();
       prefs.edit().remove("touch_scale_buttons").commit();

       prefs.edit().remove("touch_x_shift_start").commit();
       prefs.edit().remove("touch_y_shift_start").commit();
       prefs.edit().remove("touch_scale_start").commit();

       prefs.edit().remove("touch_x_shift_left_trigger").commit();
       prefs.edit().remove("touch_y_shift_left_trigger").commit();
       prefs.edit().remove("touch_scale_left_trigger").commit();

       prefs.edit().remove("touch_x_shift_right_trigger").commit();
       prefs.edit().remove("touch_y_shift_right_trigger").commit();
       prefs.edit().remove("touch_scale_right_trigger").commit();

       prefs.edit().remove("touch_x_shift_analog").commit();
       prefs.edit().remove("touch_y_shift_analog").commit();
       prefs.edit().remove("touch_scale_analog").commit();

       vjoy_d_custom = readCustomVjoyValues(context);

       resetEditMode();
       requestLayout();
  }
  
  public void restoreCustomVjoyValues(float[][] vjoy_d_cached) {
	  vjoy_d_custom = vjoy_d_cached;
	  VJoy.writeCustomVjoyValues(vjoy_d_cached, context);

      resetEditMode();
      requestLayout();
  }
  	
  public GL2JNIViewV6(Context context,String newFileName,boolean translucent,int depth,int stencil,boolean editVjoyMode)
  {
    super(context);
    this.context = context;
    this.editVjoyMode = editVjoyMode;
    setKeepScreenOn(true);
    
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
    setOnSystemUiVisibilityChangeListener (new OnSystemUiVisibilityChangeListener() {
	      public void onSystemUiVisibilityChange(int visibility) {
	        if ((visibility & SYSTEM_UI_FLAG_FULLSCREEN) == 0) {
	          GL2JNIViewV6.this.setSystemUiVisibility(
	            SYSTEM_UI_FLAG_IMMERSIVE_STICKY
	            | SYSTEM_UI_FLAG_FULLSCREEN
	            | SYSTEM_UI_FLAG_HIDE_NAVIGATION);
	        }
	      }
	    });
    }

    vib=(Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);
    
    Runtime.getRuntime().freeMemory();
	System.gc();
	
	Thread.currentThread().setPriority(Thread.MAX_PRIORITY);

    SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
    touchVibrationEnabled = prefs.getBoolean("touch_vibration_enabled", true);
    
    vjoy_d_custom = readCustomVjoyValues(context);

    scaleGestureDetector = new ScaleGestureDetector(context, new OscOnScaleGestureListener());

    // This is the game we are going to run
    fileName = newFileName;

    if (GL2JNIActivity.syms != null)
    	JNIdc.data(1, GL2JNIActivity.syms);

    JNIdc.init(fileName);

    // By default, GLSurfaceView() creates a RGB_565 opaque surface.
    // If we want a translucent one, we should change the surface's
    // format here, using PixelFormat.TRANSLUCENT for GL Surfaces
    // is interpreted as any 32-bit surface with alpha by SurfaceFlinger.
    if(translucent) this.getHolder().setFormat(PixelFormat.TRANSLUCENT);

    // Setup the context factory for 2.0 rendering.
    // See ContextFactory class definition below
    setEGLContextFactory(new ContextFactory());

    // We need to choose an EGLConfig that matches the format of
    // our surface exactly. This is going to be done in our
    // custom config chooser. See ConfigChooser class definition
    // below.
    setEGLConfigChooser(
      translucent?
        new ConfigChooser(8, 8, 8, 8, depth, stencil)
      : new ConfigChooser(5, 6, 5, 0, depth, stencil)
    );

    // Set the renderer responsible for frame rendering
    setRenderer(rend=new Renderer());
    
    ethd.start();
  }
  
  public GLSurfaceView.Renderer getRenderer()
  {
	  return rend;
  }

  private static void LOGI(String S) { Log.i("GL2JNIView",S); }
  private static void LOGW(String S) { Log.w("GL2JNIView",S); }
  private static void LOGE(String S) { Log.e("GL2JNIView",S); }

  private void reset_analog()
  {
	  
    int j=11;
    vjoy[j+1][0]=vjoy[j][0]+vjoy[j][2]/2-vjoy[j+1][2]/2;
    vjoy[j+1][1]=vjoy[j][1]+vjoy[j][3]/2-vjoy[j+1][3]/2;
    JNIdc.vjoy(j+1, vjoy[j+1][0], vjoy[j+1][1], vjoy[j+1][2], vjoy[j+1][3]);
  }
  
  int get_anal(int j, int axis)
  {
	  return (int) (((vjoy[j+1][axis]+vjoy[j+1][axis+2]/2) - vjoy[j][axis] - vjoy[j][axis+2]/2)*254/vjoy[j][axis+2]);
  }
  
  float vbase(float p, float m, float scl)
  {
	  return (int) ( m - (m -p)*scl);
  }
  
  float vbase(float p, float scl)
  {
	  return (int) (p*scl );
  }
  
  public boolean isTablet() {
    return (getContext().getResources().getConfiguration().screenLayout
            & Configuration.SCREENLAYOUT_SIZE_MASK)
            >= Configuration.SCREENLAYOUT_SIZE_LARGE;
  }

  @Override
  protected void onLayout(boolean changed, int left, int top, int right, int bottom) 
  {  
		super.onLayout(changed, left, top, right, bottom);
		//dcpx/cm = dcpx/px * px/cm
                float magic = isTablet() ? 0.8f : 0.7f;
		float scl=480.0f/getHeight() * getContext().getResources().getDisplayMetrics().density * magic;
		float scl_dc=getHeight()/480.0f;
		float tx  = ((getWidth()-640.0f*scl_dc)/2)/scl_dc;
		
		float a_x = -tx+ 24*scl;
		float a_y=- 24*scl;
		
                float[][] vjoy_d = VJoy.getVjoy_d(vjoy_d_custom);

		for(int i=0;i<vjoy.length;i++)
		{
			if (vjoy_d[i][0] == 288)
				vjoy[i][0] = vjoy_d[i][0];
			else if (vjoy_d[i][0]-vjoy_d_custom[getElementIdFromButtonId(i)][0] < 320)
				vjoy[i][0] = a_x + vbase(vjoy_d[i][0],scl);
			else
				vjoy[i][0] = -a_x + vbase(vjoy_d[i][0],640,scl);
			
			vjoy[i][1] = a_y + vbase(vjoy_d[i][1],480,scl);
			
			vjoy[i][2] = vbase(vjoy_d[i][2],scl);
			vjoy[i][3] = vbase(vjoy_d[i][3],scl);
		}
		
		for(int i=0;i<vjoy.length;i++)
		      JNIdc.vjoy(i,vjoy[i][0],vjoy[i][1],vjoy[i][2],vjoy[i][3]);
		    
		reset_analog();
		VJoy.writeCustomVjoyValues(vjoy_d_custom, context);
	}
  
  /*
   * 
   * 	DOWN / POINTER_DOWN
   * 	UP / CANCEL -> reset state
   * 	POINTER_UP -> check for freed analog
   * */
  int anal_id=-1, lt_id=-1, rt_id=-1;

  private void resetEditMode() {
        editLastX = 0;
        editLastY = 0;
  }

  private static int getElementIdFromButtonId(int buttonId) {
       if (buttonId <= 3)
            return 0; // DPAD
       else if (buttonId <= 7)
            return 1; // X, Y, B, A Buttons
       else if (buttonId == 8)
            return 2; // Start
       else if (buttonId == 9)
            return 3; // Left Trigger
       else if (buttonId == 10)
            return 4; // Right Trigger
       else if (buttonId <= 12)
            return 5; // Analog
       else
            return -1; // Invalid
  }

  static int[] kcode_raw = { 0xFFFF, 0xFFFF, 0xFFFF, 0xFFFF };
  static int[] lt = new int[4], rt = new int[4], jx = new int[4], jy = new int[4];

  float editLastX = 0, editLastY = 0;

  @Override public boolean onTouchEvent(final MotionEvent event) 
  {
  JNIdc.show_osd();

  scaleGestureDetector.onTouchEvent(event);
  
  float ty  = 0.0f;
  float scl  = getHeight()/480.0f;
  float tx  = (getWidth()-640.0f*scl)/2;

  int   rv  = 0xFFFF;
  
  int   aid = event.getActionMasked();
  int   pid = event.getActionIndex();

  if (editVjoyMode && selectedVjoyElement != -1 && aid == MotionEvent.ACTION_MOVE && !scaleGestureDetector.isInProgress()) {
       float x = (event.getX()-tx)/scl;
       float y = (event.getY()-ty)/scl;

       if (editLastX != 0 && editLastY != 0) {
            float deltaX = x - editLastX;
            float deltaY = y - editLastY;

            vjoy_d_custom[selectedVjoyElement][0] += isTablet() ? deltaX * 2 : deltaX;
            vjoy_d_custom[selectedVjoyElement][1] += isTablet() ? deltaY * 2 : deltaY;

            requestLayout();
       }

       editLastX = x;
       editLastY = y;

       return true;
  }
  
  //LOGI("Touch: " + aid + ", " + pid);
    
  for(int i=0;i<event.getPointerCount();i++)
  { 
	float x = (event.getX(i)-tx)/scl;
	float y = (event.getY(i)-ty)/scl;
	if (anal_id!=event.getPointerId(i))
	{
		if (aid==MotionEvent.ACTION_POINTER_UP && pid==i)
			continue;
		for(int j=0;j<vjoy.length;j++)
		{
		  if(x>vjoy[j][0] && x<=(vjoy[j][0]+vjoy[j][2]))
		  {
			int pre=(int)(event.getPressure(i)*255);
			if (pre>20)
			{
				pre-=20;
				pre*=7;
			}
			if (pre>255) pre=255;
			
		    if(y>vjoy[j][1] && y<=(vjoy[j][1]+vjoy[j][3]))
		    {
		    	if (vjoy[j][4]>=-2)
		    	{
		    		if (vjoy[j][5]==0)
					if (!editVjoyMode && touchVibrationEnabled)
			    			vib.vibrate(50);
		    		vjoy[j][5]=2;
		    	}
		    	
		      
		      if(vjoy[j][4]==-3)
		      {
                          if (editVjoyMode) {
                                selectedVjoyElement = 5; // Analog
                                resetEditMode();
                          } else {
        		        vjoy[j+1][0]=x-vjoy[j+1][2]/2;
        		        vjoy[j+1][1]=y-vjoy[j+1][3]/2;
        		  
        		        JNIdc.vjoy(j+1, vjoy[j+1][0], vjoy[j+1][1] , vjoy[j+1][2], vjoy[j+1][3]);
        		        anal_id=event.getPointerId(i);
                          }
	          }
		  else if (vjoy[j][4]==-4);
	          else if(vjoy[j][4]==-1) {
                          if (editVjoyMode) {
                                selectedVjoyElement = 3; // Left Trigger
                                resetEditMode();
                          } else {
                                lt[0]=pre;
                                lt_id=event.getPointerId(i);
                          }
                  }
	          else if(vjoy[j][4]==-2) {
                          if (editVjoyMode) {
                                selectedVjoyElement = 4; // Right Trigger
                                resetEditMode();
                          } else{
                                rt[0]=pre;
                                rt_id=event.getPointerId(i);
                          }
                  }
	          else {
                          if (editVjoyMode) {
                                selectedVjoyElement = getElementIdFromButtonId(j);
                                resetEditMode();
                          } else
	        	        rv&=~(int)vjoy[j][4];
                  }
	        }
		  }
		}
	  }
	  else
	  {
		  if (x<vjoy[11][0])
			  x=vjoy[11][0];
		  else if (x>(vjoy[11][0]+vjoy[11][2]))
			  x=vjoy[11][0]+vjoy[11][2];
		  
		  if (y<vjoy[11][1])
			  y=vjoy[11][1];
		  else if (y>(vjoy[11][1]+vjoy[11][3]))
			  y=vjoy[11][1]+vjoy[11][3];
		  
		  int j=11;
		  vjoy[j+1][0]=x-vjoy[j+1][2]/2;
		  vjoy[j+1][1]=y-vjoy[j+1][3]/2;
		  
		  JNIdc.vjoy(j+1, vjoy[j+1][0], vjoy[j+1][1] , vjoy[j+1][2], vjoy[j+1][3]);
		  
	  }
  }
  
  for(int j=0;j<vjoy.length;j++)
	{
		if (vjoy[j][5]==2)
			vjoy[j][5]=1;
		else if (vjoy[j][5]==1)
			vjoy[j][5]=0;
	}
  
  switch(aid)
  {
  	case MotionEvent.ACTION_UP:
  	case MotionEvent.ACTION_CANCEL:
  		selectedVjoyElement = -1;
  		reset_analog();
  		anal_id=-1;
  		rv=0xFFFF;
  		rt[0]=0;
  		lt[0]=0;
  		lt_id=-1;
  		rt_id=-1;
  		for(int j=0;j<vjoy.length;j++)
  			vjoy[j][5]=0;
	break;
	
  	case MotionEvent.ACTION_POINTER_UP:
  		if (event.getPointerId(event.getActionIndex())==anal_id)
  		{
  			reset_analog();
  			anal_id=-1;
  		}
                else if (event.getPointerId(event.getActionIndex())==lt_id)
                {
                        lt[0]=0;
  			lt_id=-1;
                }
                else if (event.getPointerId(event.getActionIndex())==rt_id)
                {
                        rt[0]=0;
  			rt_id=-1;
                }
	break;
	
  	case MotionEvent.ACTION_POINTER_DOWN:
  	case MotionEvent.ACTION_DOWN:
	break;
  }

    /*
    if(GL2JNIActivity.keys[3]!=0) rv&=~key_CONT_DPAD_RIGHT;
    if(GL2JNIActivity.keys[2]!=0) rv&=~key_CONT_DPAD_LEFT;
    if(GL2JNIActivity.keys[1]!=0) rv&=~key_CONT_A;
    if(GL2JNIActivity.keys[0]!=0) rv&=~key_CONT_B;
    */
	  
    kcode_raw[0] = rv;
    jx[0] = get_anal(11, 0);
    jy[0] = get_anal(11, 1);
    pushInput();
    return(true);
  }

private class OscOnScaleGestureListener extends
  SimpleOnScaleGestureListener {

 @Override
 public boolean onScale(ScaleGestureDetector detector) {
  if (editVjoyMode && selectedVjoyElement != -1) {
       vjoy_d_custom[selectedVjoyElement][2] *= detector.getScaleFactor();
       requestLayout();

       return true;
  }

  return false;
 }

 @Override
 public void onScaleEnd(ScaleGestureDetector detector) {
  selectedVjoyElement = -1;
 }
}

private static class ContextFactory implements GLSurfaceView.EGLContextFactory
  {
    private static final int EGL_CONTEXT_CLIENT_VERSION = 0x3098;

    public EGLContext createContext(EGL10 egl,EGLDisplay display,EGLConfig eglConfig)
    {
      int[] attrList = { EGL_CONTEXT_CLIENT_VERSION, 2, EGL14.EGL_NONE };

      LOGI("Creating OpenGL ES X context");

      checkEglError("Before eglCreateContext",egl);
      EGLContext context = egl.eglCreateContext(display,eglConfig,EGL10.EGL_NO_CONTEXT,attrList);
      checkEglError("After eglCreateContext",egl);
      return(context);
    }

    public void destroyContext(EGL10 egl,EGLDisplay display,EGLContext context)
    {
      LOGI("Destroying OpenGL ES X context");
      egl.eglDestroyContext(display,context);
    }
  }

  private static void checkEglError(String prompt,EGL10 egl)
  {
    int error;

    while((error=egl.eglGetError()) != EGL14.EGL_SUCCESS)
      LOGE(String.format("%s: EGL error: 0x%x",prompt,error));
  }

  private static class ConfigChooser implements GLSurfaceView.EGLConfigChooser
  {
    // Subclasses can adjust these values:
    protected int mRedSize;
    protected int mGreenSize;
    protected int mBlueSize;
    protected int mAlphaSize;
    protected int mDepthSize;
    protected int mStencilSize;
    private int[] mValue = new int[1];

    public ConfigChooser(int r,int g,int b,int a,int depth,int stencil)
    {
      mRedSize     = r;
      mGreenSize   = g;
      mBlueSize    = b;
      mAlphaSize   = a;
      mDepthSize   = depth;
      mStencilSize = stencil;
    }

    public EGLConfig chooseConfig(EGL10 egl, EGLDisplay display) {
    	mValue = new int[1];

        int glAPIToTry = EGLExt.EGL_OPENGL_ES3_BIT_KHR;
        int[] configSpec = null;

        do {
        	EGL14.eglBindAPI(glAPIToTry);

        	int renderableType;
        	if (glAPIToTry == EGLExt.EGL_OPENGL_ES3_BIT_KHR) {
        		renderableType = EGLExt.EGL_OPENGL_ES3_BIT_KHR;
        		// If this API does not work, try ES2 next.
        		glAPIToTry = EGL14.EGL_OPENGL_ES2_BIT;
        	} else {
        		renderableType = EGL14.EGL_OPENGL_ES2_BIT;
        		// If this API does not work, try ES next.
        		glAPIToTry = EGL14.EGL_OPENGL_ES_API;
        	}

        	configSpec = new int[] { 
        			EGL14.EGL_RED_SIZE, 4,
        			EGL14.EGL_GREEN_SIZE, 4, 
        			EGL14.EGL_BLUE_SIZE, 4,
        			EGL14.EGL_RENDERABLE_TYPE, renderableType,
        			EGL14.EGL_DEPTH_SIZE, 24,
        			EGL14.EGL_NONE
        	};

        	if (!egl.eglChooseConfig(display, configSpec, null, 0, mValue)) {
        		configSpec[9] = 16;
        		if (!egl.eglChooseConfig(display, configSpec, null, 0, mValue)) {
        			throw new IllegalArgumentException("Could not get context count");
        		}
        	}

        } while (glAPIToTry != EGL14.EGL_OPENGL_ES_API && mValue[0]<=0);

        if (mValue[0]<=0) {
            throw new IllegalArgumentException("No configs match configSpec");
        }

        // Get all matching configurations.
        EGLConfig[] configs = new EGLConfig[mValue[0]];
        if (DEBUG)
        	LOGW(String.format("%d configurations", configs.length));
        if (!egl.eglChooseConfig(display, configSpec, configs, mValue[0], mValue)) {
            throw new IllegalArgumentException("Could not get config data");
        }

        for (int i = 0; i < configs.length; ++i) {
            EGLConfig config = configs[i];
            int d = findConfigAttrib(egl, display, config,
					EGL14.EGL_DEPTH_SIZE, 0);
			int s = findConfigAttrib(egl, display, config,
					EGL14.EGL_STENCIL_SIZE, 0);

			// We need at least mDepthSize and mStencilSize bits
			if (d >= mDepthSize || s >= mStencilSize) {
				// We want an *exact* match for red/green/blue/alpha
				int r = findConfigAttrib(egl, display, config,
						EGL14.EGL_RED_SIZE, 0);
				int g = findConfigAttrib(egl, display, config,
						EGL14.EGL_GREEN_SIZE, 0);
				int b = findConfigAttrib(egl, display, config,
						EGL14.EGL_BLUE_SIZE, 0);
				int a = findConfigAttrib(egl, display, config,
						EGL14.EGL_ALPHA_SIZE, 0);

				if (r == mRedSize && g == mGreenSize && b == mBlueSize
						&& a == mAlphaSize)
					if (DEBUG) {
						LOGW(String.format("Configuration %d:", i));
						printConfig(egl, display, configs[i]);
					}
					return config;
			}
        }

        throw new IllegalArgumentException("Could not find suitable EGL config");
    }

    private int findConfigAttrib(EGL10 egl,EGLDisplay display,EGLConfig config,int attribute,int defaultValue)
    {
      return(egl.eglGetConfigAttrib(display,config,attribute,mValue)? mValue[0] : defaultValue);
    }
 
    private void printConfig(EGL10 egl,EGLDisplay display,EGLConfig config)
    {
      final int[] attributes =
      {
        EGL14.EGL_BUFFER_SIZE,
        EGL14.EGL_ALPHA_SIZE,
        EGL14.EGL_BLUE_SIZE,
        EGL14.EGL_GREEN_SIZE,
        EGL14.EGL_RED_SIZE,
        EGL14.EGL_DEPTH_SIZE,
        EGL14.EGL_STENCIL_SIZE,
        EGL14.EGL_CONFIG_CAVEAT,
        EGL14.EGL_CONFIG_ID,
        EGL14.EGL_LEVEL,
        EGL14.EGL_MAX_PBUFFER_HEIGHT,
        EGL14.EGL_MAX_PBUFFER_PIXELS,
        EGL14.EGL_MAX_PBUFFER_WIDTH,
        EGL14.EGL_NATIVE_RENDERABLE,
        EGL14.EGL_NATIVE_VISUAL_ID,
        EGL14.EGL_NATIVE_VISUAL_TYPE,
        0x3030, // EGL14.EGL_PRESERVED_RESOURCES,
        EGL14.EGL_SAMPLES,
        EGL14.EGL_SAMPLE_BUFFERS,
        EGL14.EGL_SURFACE_TYPE,
        EGL14.EGL_TRANSPARENT_TYPE,
        EGL14.EGL_TRANSPARENT_RED_VALUE,
        EGL14.EGL_TRANSPARENT_GREEN_VALUE,
        EGL14.EGL_TRANSPARENT_BLUE_VALUE,
        0x3039, // EGL14.EGL_BIND_TO_TEXTURE_RGB,
        0x303A, // EGL14.EGL_BIND_TO_TEXTURE_RGBA,
        0x303B, // EGL14.EGL_MIN_SWAP_INTERVAL,
        0x303C, // EGL14.EGL_MAX_SWAP_INTERVAL,
        EGL14.EGL_LUMINANCE_SIZE,
        EGL14.EGL_ALPHA_MASK_SIZE,
        EGL14.EGL_COLOR_BUFFER_TYPE,
        EGL14.EGL_RENDERABLE_TYPE,
        0x3042 // EGL14.EGL_CONFORMANT
      };

      final String[] names =
      {
        "EGL_BUFFER_SIZE",
        "EGL_ALPHA_SIZE",
        "EGL_BLUE_SIZE",
        "EGL_GREEN_SIZE",
        "EGL_RED_SIZE",
        "EGL_DEPTH_SIZE",
        "EGL_STENCIL_SIZE",
        "EGL_CONFIG_CAVEAT",
        "EGL_CONFIG_ID",
        "EGL_LEVEL",
        "EGL_MAX_PBUFFER_HEIGHT",
        "EGL_MAX_PBUFFER_PIXELS",
        "EGL_MAX_PBUFFER_WIDTH",
        "EGL_NATIVE_RENDERABLE",
        "EGL_NATIVE_VISUAL_ID",
        "EGL_NATIVE_VISUAL_TYPE",
        "EGL_PRESERVED_RESOURCES",
        "EGL_SAMPLES",
        "EGL_SAMPLE_BUFFERS",
        "EGL_SURFACE_TYPE",
        "EGL_TRANSPARENT_TYPE",
        "EGL_TRANSPARENT_RED_VALUE",
        "EGL_TRANSPARENT_GREEN_VALUE",
        "EGL_TRANSPARENT_BLUE_VALUE",
        "EGL_BIND_TO_TEXTURE_RGB",
        "EGL_BIND_TO_TEXTURE_RGBA",
        "EGL_MIN_SWAP_INTERVAL",
        "EGL_MAX_SWAP_INTERVAL",
        "EGL_LUMINANCE_SIZE",
        "EGL_ALPHA_MASK_SIZE",
        "EGL_COLOR_BUFFER_TYPE",
        "EGL_RENDERABLE_TYPE",
        "EGL_CONFORMANT"
      };

      int[] value = new int[1];

      for(int i=0 ; i<attributes.length ; i++)
        if(egl.eglGetConfigAttrib(display,config,attributes[i],value))
          LOGI(String.format("  %s: %d\n",names[i],value[0]));
        else
          while(egl.eglGetError()!=EGL14.EGL_SUCCESS);
    }
  }
  
  public void pushInput(){
	  JNIdc.kcode(kcode_raw,lt,rt,jx,jy);
  }

  private static class Renderer implements GLSurfaceView.Renderer
  {
    public void onDrawFrame(GL10 gl)
    {
      // Natively update nullDC display
      JNIdc.rendframe();
    }

    public void onSurfaceChanged(GL10 gl,int width,int height)
    {
    	JNIdc.rendinit(width,height);
    }

    public void onSurfaceCreated(GL10 gl,EGLConfig config)
    {
    	onSurfaceChanged(gl, 800, 480);
    }
  }


  class EmuThread extends Thread
  {
	AudioTrack Player;
	long pos;	//write position
	long size;	//size in frames
	
    @Override public void run()
    {
    	int min=AudioTrack.getMinBufferSize(44100,AudioFormat.CHANNEL_OUT_STEREO,AudioFormat.ENCODING_PCM_16BIT);
    	
    	if (2048>min)
    		min=2048;
    	
    	Player = new AudioTrack(
    	        AudioManager.STREAM_MUSIC,
    	        44100,
    	        AudioFormat.CHANNEL_OUT_STEREO,
    	        AudioFormat.ENCODING_PCM_16BIT,
    	        min,
    	        AudioTrack.MODE_STREAM
    	      );
    	
    	size=min/4; 
    	pos=0;
    	
    	Log.i("audcfg", "Audio streaming: buffer size " + min + " samples / " + min/44100.0 + " ms");
    	Player.play();
    	 
    	JNIdc.run(this);
    }
    
    int WriteBuffer(short[] samples, int wait)
    {
    	int newdata=samples.length/2;
    	
    	if (wait==0)
    	{
    		//user bytes = write-read
    		//available = size - (write - play)
    		long used=pos-Player.getPlaybackHeadPosition();
    		long avail=size-used;
    		
    		//Log.i("AUD", "u: " + used + " a: " + avail);
    		if (avail<newdata)
    			return 0;
    	}
    	
    	pos+=newdata;
    	
    	Player.write(samples, 0, samples.length);
    	
    	return 1;
    }
  }

  public void onStop() {
	  // TODO Auto-generated method stub
	  System.exit(0);
	  try {
		  ethd.join();
	  } catch (InterruptedException e) {
		  // TODO Auto-generated catch block
		  e.printStackTrace();
	  }
  }
  
  @Override
  public void onWindowFocusChanged(boolean hasFocus) {
          super.onWindowFocusChanged(hasFocus);
      if (hasFocus && Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
          GL2JNIViewV6.this.setSystemUiVisibility(
                  View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                  | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                  | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                  | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                  | View.SYSTEM_UI_FLAG_FULLSCREEN
                  | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);}
  }
}
