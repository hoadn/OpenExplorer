/*
    Open Manager For Tablets, an open source file manager for the Android system
    Copyright (C) 2011  Joe Berria <nexesdevelopment@gmail.com>

    This program is free software: you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.

    This program is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with this program.  If not, see <http://www.gnu.org/licenses/>.
*/

package org.brandroid.openmanager;

import android.net.Uri;
import android.os.Bundle;
import android.app.Activity;
import android.app.DialogFragment;
import android.app.FragmentManager;
import android.app.FragmentManager.OnBackStackChangedListener;
import android.app.FragmentTransaction;
import android.content.Intent;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.view.ActionMode;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.SearchView;
import android.widget.Toast;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.zip.Inflater;

import org.brandroid.openmanager.FileManager.SortType;

public class MainActivity extends Activity implements OnBackStackChangedListener {	
	//menu IDs
	private static final int MENU_DIR = 		0x0;
	private static final int MENU_SEARCH = 		0x1;
	private static final int MENU_MULTI =		0x2;
	private static final int MENU_SETTINGS = 	0x3;
	private static final int MENU_MODE	=		0x4;
	private static final int MENU_SORT = 		0x5;
	private static final int PREF_CODE =		0x6;
	
	private static OnSetingsChangeListener mSettingsListener;
	private SharedPreferences mPreferences;
	private ActionMode mActionMode;
	private SearchView mSearchView;
	private ArrayList<String> mHeldFiles;
	private boolean mBackQuit = false;
	
	private EventHandler mEvHandler;
	private FileManager mFileManger;
	
	private FragmentManager fragmentManager;
	
	private ActionMode.Callback mMultiSelectAction = new ActionMode.Callback() {
		MultiSelectHandler handler;
		
		
		public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
			return false;
		}
		
		
		public void onDestroyActionMode(ActionMode mode) {			
			((DirContentActivity)getFragmentManager()
					.findFragmentById(R.id.content_frag))
						.changeMultiSelectState(false, handler);
			
			mActionMode = null;
			handler = null;
		}
		
		
		public boolean onCreateActionMode(ActionMode mode, Menu menu) {
			handler = MultiSelectHandler.getInstance(MainActivity.this);
			mode.setTitle("Multi-select Options");
			
			menu.add(0, 12, 0, "Delete");
			menu.add(0, 13, 0, "Copy");
			menu.add(0, 14, 0, "Cut");
			menu.add(0, 15, 0, "Send");
			
			((DirContentActivity)getFragmentManager()
					.findFragmentById(R.id.content_frag))
						.changeMultiSelectState(true, handler);
			
			return true;
		}
		
		
		public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
			ArrayList<String>files = handler.getSelectedFiles();
			
			//nothing was selected
			if(files.size() < 1) {
				mode.finish();
				return true;
			}
			
			if(mHeldFiles == null)
				mHeldFiles = new ArrayList<String>();
			
			mHeldFiles.clear();
			
			for(String s : files)
				mHeldFiles.add(s);
			
			switch(item.getItemId()) {
			case 12: /* delete */
				mEvHandler.deleteFile(mHeldFiles);
				mode.finish();
				return true;
			
			case 13: /* copy */
				getActionBar().setTitle("Holding " + files.size() + " File");
				((DirContentActivity)getFragmentManager()
						.findFragmentById(R.id.content_frag))
							.setCopiedFiles(mHeldFiles, false);
				
				Toast.makeText(MainActivity.this, 
							   "Tap the upper left corner to see your held files",
							   Toast.LENGTH_LONG).show();
				mode.finish();
				return true;
				
			case 14: /* cut */
				getActionBar().setTitle("Holding " + files.size() + " File");
				((DirContentActivity)getFragmentManager()
						.findFragmentById(R.id.content_frag))
							.setCopiedFiles(mHeldFiles, true);
				
				Toast.makeText(MainActivity.this, 
						   "Tap the upper left corner to see your held files",
						   Toast.LENGTH_LONG).show();
				mode.finish();
				return true;
				
			case 15: /* send */
				ArrayList<Uri> uris = new ArrayList<Uri>();
				Intent mail = new Intent();
				mail.setType("application/mail");

				if(mHeldFiles.size() == 1) {
					mail.setAction(android.content.Intent.ACTION_SEND);
					mail.putExtra(Intent.EXTRA_STREAM, Uri.fromFile(new File(mHeldFiles.get(0))));
					startActivity(mail);
					
					mode.finish();
					return true;
				}
				
				for(int i = 0; i < mHeldFiles.size(); i++)
					uris.add(Uri.fromFile(new File(mHeldFiles.get(i))));
				
				mail.setAction(android.content.Intent.ACTION_SEND_MULTIPLE);
				mail.putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris);
				startActivity(mail);

//				this is for bluetooth
//				mEvHandler.sendFile(mHeldFiles);
				mode.finish();
				return true;
			}
			
			return false;
		}
	};
	
	
	public interface OnSetingsChangeListener {
		
		public void onHiddenFilesChanged(boolean state);
		public void onThumbnailChanged(boolean state);
		public void onViewChanged(String state);
		public void onSortingChanged(FileManager.SortType type);
	}
	
    
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main_fragments);
        
        fragmentManager = getFragmentManager();
        fragmentManager.addOnBackStackChangedListener(this);
        
        /*
        FragmentTransaction trans = fragmentManager.beginTransaction();
        trans.add(R.id.content_frag, new DirContentActivity());
        trans.addToBackStack(null);
        trans.commit();
        */
        //getFragmentManager().findFragmentById(R.id.content_frag);
        
                
        mEvHandler = ((DirContentActivity)getFragmentManager()
        					.findFragmentById(R.id.content_frag)).getEventHandlerInst();
        mFileManger = ((DirContentActivity)getFragmentManager()
							.findFragmentById(R.id.content_frag)).getFileManagerInst();
        
        mPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        mSearchView = new SearchView(this);
        mSearchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
        
			
			public boolean onQueryTextSubmit(String query) {
				mSearchView.clearFocus();
				mEvHandler.searchFile(mFileManger.getCurrentDir(), query);
				
				return true;
			}
			
			
			public boolean onQueryTextChange(String newText) {
				return false;
			}
		});
        
        /* read and display the users preferences */
        mSettingsListener.onHiddenFilesChanged(mPreferences.getBoolean(SettingsActivity.PREF_HIDDEN_KEY, false));
		mSettingsListener.onThumbnailChanged(mPreferences.getBoolean(SettingsActivity.PREF_THUMB_KEY, true));
		mSettingsListener.onViewChanged(mPreferences.getString(SettingsActivity.PREF_VIEW_KEY, "list"));
		//mSettingsListener.onSortingChanged(mPreferences.getString(SettingsActivity.PREF_SORT_KEY, "type"));
    }
    
    
    public boolean onCreateOptionsMenu(Menu menu) {
    	getMenuInflater().inflate(R.menu.actbar, menu);
    	return true;
    }


    
    public boolean onOptionsItemSelected(MenuItem item)
    {
    	if(item.isCheckable())
    		item.setChecked(item.getGroupId() > 0 ? true : !item.isChecked());
    	
    	switch(item.getItemId())
    	{
	    	case android.R.id.home:
	    		if (mHeldFiles != null) {
	    			//DialogFragment df = 
	    			DialogHandler dialog = DialogHandler.newDialog(DialogHandler.HOLDINGFILE_DIALOG, this);
	    			dialog.setHoldingFileList(mHeldFiles);
	    			
	    			FragmentTransaction trans = fragmentManager.beginTransaction();
	    			trans.add(dialog, "dialog");
	    			//dialog.show(getFragmentManager(), "dialog");
	    			trans.addToBackStack("dialog");
	    			trans.commit();
	    		}
	    		return true;
	    	
	    	case R.id.menu_new_folder:
	    	case MENU_DIR:
	    		mEvHandler.createNewFolder(mFileManger.getCurrentDir());
	    		return true;
	    		
	    	case R.id.menu_multi:
	    	case MENU_MULTI:
	    		if(mActionMode != null)
	    			return false;
	    		
	    		mActionMode = startActionMode(mMultiSelectAction);
	    		return true;
	    		
	    	case MENU_SORT:
	    		return true;
	    	
	    	case R.id.menu_sort_name_asc:	mSettingsListener.onSortingChanged(FileManager.SortType.ALPHA); return true; 
	    	case R.id.menu_sort_name_desc:	mSettingsListener.onSortingChanged(FileManager.SortType.ALPHA_DESC); return true; 
	    	case R.id.menu_sort_date_asc: 	mSettingsListener.onSortingChanged(FileManager.SortType.DATE); return true;
	    	case R.id.menu_sort_date_desc: 	mSettingsListener.onSortingChanged(FileManager.SortType.DATE_DESC); return true; 
	    	case R.id.menu_sort_size_asc: 	mSettingsListener.onSortingChanged(FileManager.SortType.SIZE); return true; 
	    	case R.id.menu_sort_size_desc: 	mSettingsListener.onSortingChanged(FileManager.SortType.SIZE_DESC); return true; 
	    	case R.id.menu_sort_type: 		mSettingsListener.onSortingChanged(FileManager.SortType.TYPE); return true;
	    	
	    	case R.id.menu_view_grid: mSettingsListener.onViewChanged("grid"); return true;
	    	case R.id.menu_view_list: mSettingsListener.onViewChanged("list"); return true;
	    	case R.id.menu_view_hidden: mSettingsListener.onHiddenFilesChanged(item.isChecked()); return true;
	    	case R.id.menu_view_thumbs: mSettingsListener.onThumbnailChanged(item.isChecked()); return true;
	    	
	    	case R.id.menu_root:
	    		if(!item.isCheckable() || item.isChecked())
	    		{
	    			if(ExecuteAsRootBase.canRunRootCommands())
	    				item.setTitle("ROOT!");
	    			else
	    				item.setEnabled(false).setChecked(false);
	    		}
	    		return true;
	    	
	    	case R.id.menu_settings:
	    	case MENU_SETTINGS:
	    		FragmentTransaction trans = fragmentManager.beginTransaction();
	    		SettingsActivity frag = new SettingsActivity();
	    		trans.add(R.id.content_frag, frag);
	    		trans.addToBackStack("Settings");
	    		trans.commit();
	    		//startActivityForResult(new Intent(this, SettingsActivity.class), PREF_CODE);
	    		return true;
	    		
	    	case R.id.menu_search:
	    	case MENU_SEARCH:
	    		item.setActionView(mSearchView);
	    		return true;
    	}
    	
    	return super.onOptionsItemSelected(item);
    }
    
    public boolean onKeyUp(int keyCode, KeyEvent event) {
    	if (keyCode == KeyEvent.KEYCODE_BACK) {
    		if (mBackQuit) {
    			return super.onKeyUp(keyCode, event);
    		} else {
    			Toast.makeText(this, "Press back again to quit", Toast.LENGTH_SHORT).show();
    			mBackQuit = true;
    			return true;
    		}    	
    	}
    	return super.onKeyUp(keyCode, event);
    }
    
    public static void setOnSetingsChangeListener(OnSetingsChangeListener e) {
    	mSettingsListener = e;
    }
    
    /*
     * used to inform the user when they are holding a file to copy, zip, et cetera
     * When the user does something with the held files (from copy or cut) this is 
     * called to reset the apps title. When that happens we will get rid of the cached
     * held files if there are any.  
     * @param title the title to be displayed
     */
    public void changeActionBarTitle(String title) {
    	if (title.equals("Open Manager") && mHeldFiles != null) {
	    	mHeldFiles.clear();
	    	mHeldFiles = null;
    	}
    	getActionBar().setTitle(title);
    }
    
    
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
    	if(requestCode == PREF_CODE) {
    		//this could be done better.
    		mSettingsListener.onHiddenFilesChanged(mPreferences.getBoolean(SettingsActivity.PREF_HIDDEN_KEY, false));
    		mSettingsListener.onThumbnailChanged(mPreferences.getBoolean(SettingsActivity.PREF_THUMB_KEY, false));
    		mSettingsListener.onViewChanged(mPreferences.getString(SettingsActivity.PREF_VIEW_KEY, "list"));
    		//mSettingsListener.onSortingChanged(mPreferences.getString(SettingsActivity.PREF_SORT_KEY, "alpha"));
    	}
    }
    
    
    protected void onPause() {
    	super.onPause();
    	String list = ((DirListActivity)getFragmentManager()
    					.findFragmentById(R.id.list_frag)).getDirListString();
    	String bookmark = ((DirListActivity)getFragmentManager()
    					.findFragmentById(R.id.list_frag)).getBookMarkNameString();
    	
    	String saved = mPreferences.getString(SettingsActivity.PREF_LIST_KEY, "");
    	String saved_book = mPreferences.getString(SettingsActivity.PREF_BOOKNAME_KEY, "");
    	
    	if (!list.equals(saved)) {
    		SharedPreferences.Editor e = mPreferences.edit();
    		e.putString(SettingsActivity.PREF_LIST_KEY, list);
    		e.commit();
    	}
    	
    	if (!bookmark.equals(saved_book)) {
    		SharedPreferences.Editor e = mPreferences.edit();
    		e.putString(SettingsActivity.PREF_BOOKNAME_KEY, bookmark);
    		e.commit();
    	}
    }

	
	public void onBackStackChanged() {
		//fragmentManager.
	}
}


