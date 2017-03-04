package com.example.baddriverreporter;


import android.app.Activity;
import android.app.FragmentManager;
import android.app.FragmentTransaction;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.FrameLayout;

import com.example.baddriverreporter.fragments.Camera2VideoFragment;
import com.example.baddriverreporter.fragments.MapFragment;


public class MainActivity extends Activity {

	String mapTag = "MAP_FRAGMENT_TAG";
	String camTag = "CAMERA_FRAGMENT_TAG";

	FragmentManager mFragmentManager;
	MapFragment map;
	Camera2VideoFragment camera;
	FrameLayout recodeBtnContainer;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		requestWindowFeature(Window.FEATURE_NO_TITLE);
		getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
				WindowManager.LayoutParams.FLAG_FULLSCREEN);
//      setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);

		setContentView(R.layout.activity_main);

		//first time to create the activity
		if(savedInstanceState==null) {
			camera = new Camera2VideoFragment();
			camera.setUpGoogleApiClient(this);
			map = new MapFragment();
			mFragmentManager = getFragmentManager();

			mFragmentManager
					.beginTransaction()
					.add(R.id.bottomFrameLayout, map, mapTag)
					.add(R.id.mainFrameLayout, camera, camTag)
					.commit();
			((ClickableFrameLayout)findViewById(R.id.bottomFrameLayout))
					.setOnClickListener(new View.OnClickListener() {
						@Override
						public void onClick(View view) {
							int curMapView = ((ViewGroup)map.getView().getParent()).getId(),
									curCamView = ((ViewGroup)camera.getView().getParent()).getId();
							mFragmentManager
									.beginTransaction()
									.remove(map)
									.remove(camera)
									.commit();
							//commit is asynchronized, so we need it
							mFragmentManager.executePendingTransactions();
							mFragmentManager
									.beginTransaction()
									.setTransition(FragmentTransaction.TRANSIT_FRAGMENT_OPEN)
									.replace(curMapView, camera, camTag)
									.replace(curCamView, map, mapTag)
									.commit();
							mFragmentManager.executePendingTransactions();
							if(curCamView == R.id.mainFrameLayout){
								recodeBtnContainer =  (FrameLayout)(camera.getView().findViewById(R.id.video).getParent());
								recodeBtnContainer.setVisibility(FrameLayout.GONE);
							}
						}
					});
		}
	}

}
