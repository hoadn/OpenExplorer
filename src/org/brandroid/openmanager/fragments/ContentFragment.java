/*
    Open Explorer, an open source file explorer & text editor
    Copyright (C) 2011 Brandon Bowles <brandroid64@gmail.com>

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

package org.brandroid.openmanager.fragments;

import org.brandroid.openmanager.OpenExplorer;
import org.brandroid.openmanager.R;
import org.brandroid.openmanager.SettingsActivity;
import org.brandroid.openmanager.adapters.IconContextMenu;
import org.brandroid.openmanager.adapters.IconContextMenuAdapter;
import org.brandroid.openmanager.adapters.IconContextMenu.IconContextItemSelectedListener;
import org.brandroid.openmanager.data.BookmarkHolder;
import org.brandroid.openmanager.data.OpenCursor;
import org.brandroid.openmanager.data.OpenFTP;
import org.brandroid.openmanager.data.OpenMediaStore;
import org.brandroid.openmanager.data.OpenPath;
import org.brandroid.openmanager.data.OpenFile;
import org.brandroid.openmanager.fragments.DialogHandler.OnSearchFileSelected;
import org.brandroid.openmanager.util.EventHandler;
import org.brandroid.openmanager.util.FileManager;
import org.brandroid.openmanager.util.IntentManager;
import org.brandroid.openmanager.util.MultiSelectHandler;
import org.brandroid.openmanager.util.ThumbnailCreator;
import org.brandroid.openmanager.util.EventHandler.OnWorkerThreadFinishedListener;
import org.brandroid.openmanager.util.FileManager.SortType;
import org.brandroid.utils.Logger;
import org.brandroid.utils.MenuBuilder;

import java.io.File;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;

import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.app.AlertDialog.Builder;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.view.ActionMode;
import android.view.ContextMenu;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.View.OnLongClickListener;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.AbsListView.OnScrollListener;
import android.widget.AdapterView.AdapterContextMenuInfo;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Toast;
import android.widget.ArrayAdapter;
import android.widget.LinearLayout;
import android.widget.GridView;
import android.widget.ListView;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.AdapterView.OnItemLongClickListener;
import android.net.Uri;

public class ContentFragment extends OpenFragment implements OnItemClickListener,
															OnWorkerThreadFinishedListener{
	
	public static final boolean USE_ACTIONMODE = false;
	private static boolean mMultiSelectOn = false;
	
	private FileManager mFileManager;
	private EventHandler mHandler;
	private MultiSelectHandler mMultiSelect;
	private static OnBookMarkAddListener mBookmarkList;
	
	//private LinearLayout mPathView;
	private LinearLayout mMultiSelectView;
	private GridView mGrid = null;
	private ListView mList = null;
	
	private OpenPath mPath = null;
	private static OpenPath mLastPath = null;
	private OpenPath[] mData; 
	private ArrayList<OpenPath> mData2 = null; //the data that is bound to our array adapter.
	private Context mContext;
	private FileSystemAdapter mContentAdapter;
	private ActionMode mActionMode = null;
	private boolean mActionModeSelected;
	private boolean mCutFile = false;
	private boolean mShowThumbnails = true;
	private boolean mReadyToUpdate = true;
	private int mMenuContextItemIndex = -1;
	private int mListScrollingState = 0;
	private int mListVisibleStartIndex = 0;
	private int mListVisibleLength = 0; 
	public Boolean mShowLongDate = false; 
	
	public interface OnBookMarkAddListener {
		public void onBookMarkAdd(OpenPath path);
	}
	
	public ContentFragment()
	{
		//Logger.LogDebug("Creating empty ContentFragment", new Exception("Creating empty ContentFragment"));
		mPath = mLastPath;
	}
	public ContentFragment(OpenPath path)
	{
		mPath = mLastPath = path;
	}
	
	private int getViewMode() { return ((OpenExplorer)getActivity()).getViewMode(); }
	
	//@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		
		if(savedInstanceState != null && savedInstanceState.containsKey("last"))
			mPath = mLastPath = new OpenFile(savedInstanceState.getString("last"));
		
		if(mPath == null)
			Logger.LogDebug("Creating empty ContentFragment", new Exception("Creating empty ContentFragment"));
		
		mContext = getActivity().getApplicationContext();
		
		OpenExplorer explorer = ((OpenExplorer)getActivity());
		mFileManager = explorer.getFileManager();
		if(mFileManager == null)
		{
			mFileManager = new FileManager();
			explorer.setFileManager(mFileManager);
		}
		mHandler = explorer.getEventHandler();
		if(mHandler == null)
		{
			mHandler = new EventHandler(mFileManager);
			explorer.setEventHandler(mHandler);
		}
		mHandler.setOnWorkerThreadFinishedListener(this);
		refreshData(savedInstanceState);
	}
	public void refreshData(Bundle savedInstanceState)
	{
		if(mData2 == null)
			mData2 = new ArrayList<OpenPath>();
		else
			mData2.clear();
		
		OpenPath path = mPath;
		if(path == null) path = mLastPath;
		if(path == null)
		{
			if (savedInstanceState != null && savedInstanceState.containsKey("last"))
				path = new OpenFile(savedInstanceState.getString("last"));
			else
				path = new OpenFile(Environment.getExternalStorageDirectory());
		}
		mData = mFileManager.getChildren(path);
		
		mActionModeSelected = false;
		try {
			mShowThumbnails = ((OpenExplorer)getActivity()).getPreferences()
								.getSetting(mPath.getPath(), SettingsActivity.PREF_THUMB_KEY, true);
		} catch(NullPointerException npe) {
			mShowThumbnails = true;
		}

		if(path.getClass().equals(OpenCursor.class) && !OpenExplorer.BEFORE_HONEYCOMB)
			mShowThumbnails = true;
		
		mShowLongDate = getActivity().getWindow().getWindowManager().getDefaultDisplay().getWidth() > 500
				&& mPath != null
				&& OpenFile.class.equals(mPath.getClass());
		
		//OpenExplorer.setOnSettingsChangeListener(this);
		
		updateData(mData);
	}

	//@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
		View v = inflater.inflate(R.layout.content_layout, container, false);
		//v.setBackgroundResource(R.color.lightgray);
		
		/*
		if (savedInstanceState != null && savedInstanceState.containsKey("location")) {
			String location = savedInstanceState.getString("location");
			if(location != null && !location.equals("") && location.startsWith("/"))
			{
				Logger.LogDebug("Content location restoring to " + location);
				mPath = new OpenFile(location);
				mData = mFileManager.getChildren(mPath);
				updateData(mData);
			}
			//setContentPath(path, false);
		}
		*/

		return v;
	}
	
	@Override
	public void onSaveInstanceState(Bundle outState) {
		super.onSaveInstanceState(outState);
		/*
		if(mPath != null && mPath.getPath() != null)
		{
			Logger.LogDebug("Content location saving to " + mPath.getPath());
			outState.putString("location", mPath.getPath());
		}
		*/
	}
	
	@Override
	public void onViewCreated(View v, Bundle savedInstanceState) {
		super.onViewCreated(v, savedInstanceState);
		
		//mPathView = (LinearLayout)v.findViewById(R.id.scroll_path);
		mGrid = (GridView)v.findViewById(R.id.grid_gridview);
		mList = (ListView)v.findViewById(R.id.list_listview);
		mMultiSelectView = (LinearLayout)v.findViewById(R.id.multiselect_path);

		if(mGrid == null && mList == null)
			Logger.LogError("WTF, where are they?");
		else if (getViewMode() == OpenExplorer.VIEW_GRID)
			updateChosenMode(mGrid);
		else
			updateChosenMode(mList);
	}
	
	@Override
	public boolean onContextItemSelected(MenuItem item) {
		OpenPath path = mData2.get(mMenuContextItemIndex);
		Logger.LogDebug("Showing context for " + path.getName() + "?");
		return executeMenu(item.getItemId(), path);
		//return super.onContextItemSelected(item);
	}
	
	public void updateChosenMode(final AbsListView mChosenMode)
	{
		if(getViewMode() == OpenExplorer.VIEW_GRID) {
			mContentAdapter = new FileSystemAdapter(mContext, R.layout.grid_content_layout, mData2);
			mList.setVisibility(View.GONE);
			mGrid.setVisibility(View.VISIBLE);
			mGrid.setAdapter(mContentAdapter);
		} else {
			mContentAdapter = new FileSystemAdapter(mContext, R.layout.list_content_layout, mData2);
			mGrid.setVisibility(View.GONE);
			mList.setVisibility(View.VISIBLE);
			mList.setAdapter(mContentAdapter);
		}
		mChosenMode.setVisibility(View.VISIBLE);
		mChosenMode.setOnItemClickListener(this);
		mChosenMode.setOnScrollListener(new OnScrollListener() {
			public void onScrollStateChanged(AbsListView view, int scrollState) {
				mListScrollingState = scrollState;
				if(scrollState == 0)
					onScrollStopped(view);
			}
			
			public void onScroll(AbsListView view, int firstVisibleItem, int visibleItemCount, int totalItemCount)
			{
				if(firstVisibleItem != mListVisibleStartIndex)
					mListVisibleStartIndex = firstVisibleItem;
				if(visibleItemCount != mListVisibleLength)
					mListVisibleLength = visibleItemCount;
			}
		});
		//mChosenMode.setOnCreateContextMenuListener(this);
		//if(cm == null)
		mChosenMode.setOnItemLongClickListener(new OnItemLongClickListener() {
			//@Override
			public boolean onItemLongClick(AdapterView<?> list, View view ,int pos, long id) {
				final OpenPath file = mData2.get(pos);
				final String name = file.getPath().substring(file.getPath().lastIndexOf("/")+1);
				if(OpenExplorer.BEFORE_HONEYCOMB || !USE_ACTIONMODE) {
					mMenuContextItemIndex = pos;
					final IconContextMenu cm = new IconContextMenu(getActivity(), R.menu.context_file);
					MenuBuilder cmm = cm.getMenu();
					if(getHoldingFiles().size() > 0)
						cmm.hideItem(R.id.menu_context_multi);
					else
						cmm.hideItem(R.id.menu_context_paste);
					if(!name.toLowerCase().endsWith(".zip"))
						cmm.hideItem(R.id.menu_context_unzip);
					cm.setTitle(name);
					cm.setOnIconContextItemSelectedListener(new IconContextItemSelectedListener() {	
						public void onIconContextItemSelected(MenuItem item, Object info) {
							executeMenu(item.getItemId(), mData2.get((Integer)info));
						}
					});
					cm.setInfo(pos);
					cm.show();
					//return list.showContextMenu();
					return true;
				}
				
				if(!file.isDirectory() && mActionMode == null && !mMultiSelectOn) {
					mActionMode = getActivity().startActionMode(new ActionMode.Callback() {
						//@Override
						public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
							return false;
						}
						
						//@Override
						public void onDestroyActionMode(ActionMode mode) {
							mActionMode = null;
							mActionModeSelected = false;
						}
						
						//@Override
						public boolean onCreateActionMode(ActionMode mode, Menu menu) {
							mode.getMenuInflater().inflate(R.menu.context_file, menu);
				    		
				    		mActionModeSelected = true;
							return true;
						}

						//@Override
						public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
							//ArrayList<OpenPath> files = new ArrayList<OpenPath>();
							
							//OpenPath file = mLastPath.getChild(mode.getTitle().toString());
							//files.add(file);
							
							if(item.getItemId() != R.id.menu_context_cut && item.getItemId() != R.id.menu_context_multi && item.getItemId() != R.id.menu_context_copy)
							{
								mode.finish();
								mActionModeSelected = false;
							}
							return executeMenu(item.getItemId(), mode, file);
						}
					});
					mActionMode.setTitle(file.getName());
					
					return true;
				}
				
				if(file.isDirectory() && mActionMode == null && !mMultiSelectOn) {
					if(!OpenExplorer.BEFORE_HONEYCOMB && USE_ACTIONMODE)
					mActionMode = getActivity().startActionMode(new ActionMode.Callback() {
						
						//@Override
						public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
							return false;
						}
						
						//@Override
						public void onDestroyActionMode(ActionMode mode) {
							mActionMode = null;
							mActionModeSelected = false;
						}
						
						//@Override
						public boolean onCreateActionMode(ActionMode mode, Menu menu) {
							mode.getMenuInflater().inflate(R.menu.context_file, menu);
							menu.findItem(R.id.menu_context_paste).setEnabled(isHoldingFiles());
							//menu.findItem(R.id.menu_context_unzip).setEnabled(mHoldingZip);
				        	
				        	mActionModeSelected = true;
							
				        	return true;
						}
						
						//@Override
						public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
							return executeMenu(item.getItemId(), mode, file);
						}
					});
					mActionMode.setTitle(file.getName());
					
					return true;
				}
				
				return false;
			}
		});
		if(OpenExplorer.BEFORE_HONEYCOMB && USE_ACTIONMODE)
			registerForContextMenu(mChosenMode);
	}
	
	protected void onScrollStopped(AbsListView view)
	{
		boolean skipThis = true;
		if(skipThis) return;
		int start = Math.max(0, mListVisibleStartIndex);
		int end = Math.min(mData2.size() - 1, mListVisibleStartIndex + mListVisibleLength);
		int mWidth = 96;
		int mHeight = 96;
		//ThumbnailStruct[] thumbs = ThumbnailStruct[end - start];
		for(int i = start; i < end; i++)
		{
			Object o = view.getItemAtPosition(i);
			if(o != null)
			{
				OpenPath file = (OpenPath)o;
				if(file.getTag() != null && file.getTag().getClass().equals(BookmarkHolder.class))
				{
					BookmarkHolder mHolder = (BookmarkHolder)file.getTag();
					ImageView v = mHolder.getIconView();
					//thumbs[i - start] = new ThumbnailStruct(file, mHolder, mWidth, mHeight);
					//new ThumbnailTask().execute(new ThumbnailStruct(file, mHolder, mWidth, mHeight));
				}
			}
			//view.getItemAtPosition(i);
		}
		//Logger.LogDebug("Visible items " + mData2.get(mListVisibleStartIndex).getName() + " - " + mData2.get().getName());
	}
	
	private ArrayList<OpenPath> getHoldingFiles() {
		return ((OpenExplorer)getActivity()).getHoldingFiles();
	}
	private void addHoldingFile(OpenPath path) {
		((OpenExplorer)getActivity()).addHoldingFile(path);
	}
	private void clearHoldingFiles() { ((OpenExplorer)getActivity()).clearHoldingFiles(); }
	private boolean isHoldingFiles() { return getHoldingFiles().size() > 0; }
	
	private void finishMode(Object mode)
	{
		if(!OpenExplorer.BEFORE_HONEYCOMB && mode != null)
			((ActionMode)mode).finish();
	}
	
	public boolean executeMenu(final int id, OpenPath file)
	{
		return executeMenu(id, null, file);
	}
	public boolean executeMenu(final int id, Object mode, OpenPath file)
	{
		ArrayList<OpenPath> files = new ArrayList<OpenPath>();
		files.add(file);
		return executeMenu(id, mode, file, null);
	}
	public boolean executeMenu(final int id, final Object mode, final OpenPath file, ArrayList<OpenPath> fileList)
	{
		final String path = file != null ? file.getPath() : null;
		final OpenPath folder = file != null ? file.getParent() : null;
		String name = file != null ? file.getName() : null;
		if(fileList == null)
			fileList = new ArrayList<OpenPath>();
		final OpenPath[] fileArray = new OpenPath[fileList.size()];
		fileList.toArray(fileArray);
		
		switch(id) {
			case R.id.menu_context_multi:
				changeMultiSelectState(!mMultiSelectOn, MultiSelectHandler.getInstance(mContext));
				addHoldingFile(file);
				return true;
			case R.id.menu_multi:
				changeMultiSelectState(!mMultiSelectOn, MultiSelectHandler.getInstance(mContext));
				return true;
			case R.id.menu_context_bookmark:
				if(mBookmarkList != null)
					mBookmarkList.onBookMarkAdd(file);
				else
					((OpenExplorer)getActivity()).addBookmark(file);
				finishMode(mode);
				return true;
				
			case R.id.menu_context_delete:
				fileList.add(file);
				mHandler.deleteFile(fileList, getActivity());
				finishMode(mode);
				mContentAdapter.notifyDataSetChanged();
				return true;
				
			case R.id.menu_context_rename:
				mHandler.renameFile(file.getPath(), true, getActivity());
				finishMode(mode);
				return true;
				
			case R.id.menu_context_copy:
			case R.id.menu_context_cut:
				mCutFile = id == R.id.menu_context_cut;

				addHoldingFile(file);
				return false;

			case R.id.menu_context_paste:
			case R.id.menu_paste:
				OpenPath into = id == R.id.menu_paste || !file.isDirectory() ? folder : file;
				if(getHoldingFiles().size() > 0)
					if(mCutFile)
						mHandler.cutFile(getHoldingFiles(), into, getActivity());
					else
						mHandler.copyFile(getHoldingFiles(), into, getActivity());
				
				mCutFile = false;
				clearHoldingFiles();
				((OpenExplorer)getActivity()).updateTitle(path);
				finishMode(mode);
				return true;
				
			case R.id.menu_context_zip:
				addHoldingFile(file);
				final String def = getHoldingFiles().size() == 1 ?
						file.getName() + ".zip" :
						file.getParent().getName() + ".zip";
				
				final DialogBuilder dZip = new DialogBuilder(mContext);
				dZip
					.setMessage("Enter filename of new Zip file:")
					.setDefaultText(def)
					.setIcon(getResources().getDrawable(R.drawable.zip))
					.setTitle("Zip")
					.setCancelable(true)
					.setPositiveButton("OK",
						new OnClickListener() {
							public void onClick(DialogInterface di, int which) {
								if(which != DialogInterface.BUTTON_POSITIVE) return;
								OpenPath zipFile = folder.getChild(dZip.getInputText());
								Logger.LogInfo("Zipping " + fileArray.length + " items to " + zipFile.getPath());
								mHandler.zipFile(zipFile, fileArray, getActivity());
								finishMode(mode);
							}
						})
					.create().show();
				return true;
				
			case R.id.menu_context_unzip:
				mHandler.unZipFileTo(getHoldingFiles().get(0), file, getActivity());
				
				clearHoldingFiles();
				((OpenExplorer)getActivity()).updateTitle("");
				return true;
			
			case R.id.menu_context_info:
				((OpenExplorer)getActivity()).showFileInfo(file);
				finishMode(mode);
				return true;
				
			case R.id.menu_context_share:
				
				// TODO: WTF is this?
				Intent mail = new Intent();
				mail.setType("application/mail");
				
				mail.setAction(android.content.Intent.ACTION_SEND);
				mail.putExtra(Intent.EXTRA_STREAM, Uri.fromFile(new File(path)));
				startActivity(mail);
				
				//mode.finish();
				return true;
	
	//			this is for bluetooth
	//			files.add(path);
	//			mHandler.sendFile(files);
	//			mode.finish();
	//			return true;
			}
		return true;
	}
		
	@Override
	public void onCreateContextMenu(ContextMenu menu, View v, ContextMenuInfo menuInfo) {
		super.onCreateContextMenu(menu, v, menuInfo);
		if(!OpenExplorer.BEFORE_HONEYCOMB && USE_ACTIONMODE) return;
		AdapterContextMenuInfo info = (AdapterContextMenuInfo)menuInfo;
		OpenPath file = mData2.get(info != null ? info.position : mMenuContextItemIndex);
		new MenuInflater(mContext).inflate(R.menu.context_file, menu);
		menu.findItem(R.id.menu_context_paste).setEnabled(isHoldingFiles());
	}
	
	//@Override
	public void onItemClick(AdapterView<?> list, View view, int pos, long id) {
		final OpenPath file = (OpenPath)list.getItemAtPosition(pos);
		
		//((OpenExplorer)getActivity()).hideBookmarkTitles();
		
		if(mMultiSelectOn) {
			View v;
			
			//if (mThumbnail == null)
				v = mMultiSelect.addFile(file.getPath());
			//else
			//	v = mMultiSelect.addFile(file.getPath(), mThumbnail);
			
			if(v == null)
				return;
			
			v.setOnClickListener(new View.OnClickListener() {
				//@Override
				public void onClick(View v) {					
					int ret = mMultiSelect.clearFileEntry(file.getPath());
					mMultiSelectView.removeViewAt(ret);
				}
			});
			
			mMultiSelectView.addView(v);
			return;
		}
		
		if(file.isDirectory() && !mActionModeSelected ) {
			/* if (mThumbnail != null) {
				mThumbnail.setCancelThumbnails(true);
				mThumbnail = null;
			} */
			
			
			//setContentPath(file, true);
			((OpenExplorer)getActivity()).onChangeLocation(file);

		} else if (!file.isDirectory() && !mActionModeSelected ) {
			
			if(file.requiresThread())
			{
				/// TODO: handle networked files
				((OpenExplorer)getActivity()).showToast("Still need to handle this.");
				return;
			}
			
			IntentManager.startIntent(file, (OpenExplorer)getActivity(), mHandler);
		}
	}
	
	private void updateData(final OpenPath[] items) {
		if(!mReadyToUpdate) return;
		mReadyToUpdate = false;
		
		OpenPath.Sorting = mFileManager.getSorting();
		Arrays.sort(items);
		
		mData2.clear();
		int folder_index = 0;
		for(OpenPath f : items)
		{
			if(f.isHidden() && !mFileManager.getShowHiddenFiles())
				continue;
			if(f.isDirectory())
				mData2.add(folder_index++, f);
			else
				mData2.add(f);
		}
		//Logger.LogDebug("mData has " + mData2.size());
		if(mContentAdapter != null)
			mContentAdapter.notifyDataSetChanged();
		mReadyToUpdate = true;
		/*
		mPathView.removeAllViews();
		mBackPathIndex = 0;	
		 */
	}
	
	/*
	 * (non-Javadoc)
	 * this will update the data shown to the user after a change to
	 * the file system has been made from our background thread or EventHandler.
	 */
	//@Override
	public void onWorkerThreadComplete(int type, ArrayList<String> results) {
		
		if(type == EventHandler.SEARCH_TYPE) {
			if(results == null || results.size() < 1) {
				Toast.makeText(mContext, "Sorry, zero items found", Toast.LENGTH_LONG).show();
				return;
			}
			
			DialogHandler dialog = DialogHandler.newDialog(DialogHandler.DialogType.SEARCHRESULT_DIALOG, mContext);
			ArrayList<OpenPath> files = new ArrayList<OpenPath>();
			for(String s : results)
				files.add(new OpenFile(s));
			dialog.setHoldingFileList(files);
			dialog.setOnSearchFileSelected(new OnSearchFileSelected() {
				
				//@Override
				public void onFileSelected(String fileName) {
					OpenPath file = FileManager.getOpenCache(fileName);
					
					if (file.isDirectory()) {
						changePath(mFileManager.pushStack(file));
					} else {
						changePath(mFileManager.pushStack(file.getParent()));
					}						
				}
			});
			
			dialog.show(getFragmentManager(), "dialog");
			
		} else if(type == EventHandler.UNZIPTO_TYPE && results != null) {
			String name = new OpenFile(results.get(0)).getName();
			
			addHoldingFile(new OpenFile(results.get(0)));
			((OpenExplorer)getActivity()).updateTitle("Holding " + name);
			
		} else {
			Logger.LogDebug("Worker thread complete?");
			mContentAdapter.notifyDataSetChanged();
			//changePath(mFileManager.peekStack(), false);
		}
	}
	
	public void changePath(OpenPath path) { changePath(path, true); }
	public void changePath(OpenPath path, Boolean addToStack) {
		if(path == null)
			path = new OpenFile(Environment.getExternalStorageDirectory());
		((OpenExplorer)getActivity()).changePath(path, addToStack);
	}
	
	//@Override
	public void onHiddenFilesChanged(boolean state) {
		mFileManager.setShowHiddenFiles(state);
		refreshData(null);
	}

	//@Override
	public void onThumbnailChanged(boolean state) {
		mShowThumbnails = state;
		refreshData(null);
	}
	
	//@Override
	public void onSortingChanged(SortType type) {
		mFileManager.setSorting(type);
		refreshData(null);
	}
	
	public void setSettings(SortType sort, boolean thumbs, boolean hidden)
	{
		mFileManager.setSorting(sort);
		mShowThumbnails = thumbs;
		mFileManager.setShowHiddenFiles(hidden);
		refreshData(null);
	}

	//@Override
	public void onViewChanged(int state) {
		//mViewMode = state;
		((OpenExplorer)getActivity()).setViewMode(state);
		
		View v = getView();
		if(v != null)
		{
			//if(mPathView == null)
			//	mPathView = (LinearLayout)v.findViewById(R.id.scroll_path);
			if(mGrid == null)
				mGrid = (GridView)v.findViewById(R.id.grid_gridview);
			if(mList == null)
				mList = (ListView)v.findViewById(R.id.list_listview);
			if(mMultiSelectView == null)
				mMultiSelectView = (LinearLayout)v.findViewById(R.id.multiselect_path);
		}

		if(mGrid == null && mList == null)
			Logger.LogError("WTF, where are they?");
		else if (getViewMode() == OpenExplorer.VIEW_GRID)
			updateChosenMode(mGrid);
		else
			updateChosenMode(mList);
	}
			
	public void changeMultiSelectState(boolean state, MultiSelectHandler handler) {
		if(state && handler != null) {
			mMultiSelect = handler;
			mMultiSelectOn = state;
			
		} else if (!state && handler != null) {
			mMultiSelect = handler;
			mMultiSelect.cancelMultiSelect();
			mMultiSelectView.removeAllViews();
			mMultiSelectOn = state;
		}
	}
	
	public static void setOnBookMarkAddListener(BookmarkFragment bookmarkFragment) {
		mBookmarkList = bookmarkFragment;
	}
	
	/*
	 * This is a convience function so our applications main activity
	 * (MainActivity.java) class can have access to our event handler
	 * and perform file operations such as delete, rename etc. 
	 * Event handler sits between our view and modal (FileManager)
	 */
	public EventHandler getEventHandlerInst() {
		return ((OpenExplorer)getActivity()).getEventHandler();
	}
	
	/*
	 * See comments for getEventHandlerInst(). Same reasoning.
	 */
	public FileManager getFileManagerInst() {
		return ((OpenExplorer)getActivity()).getFileManager();
	}
	
	/**
	 * 
	 */
	public class FileSystemAdapter extends ArrayAdapter<OpenPath> {
		private final int KB = 1024;
    	private final int MG = KB * KB;
    	private final int GB = MG * KB;
    	
		private BookmarkHolder mHolder;
		private String mName;
		private ThumbnailCreator mThumbnail;
		
		public FileSystemAdapter(Context context, int layout, ArrayList<OpenPath> data) {
			super(context, layout, data);
			mThumbnail = new ThumbnailCreator(mContext, handler);
		}
		
		@Override protected void finalize() throws Throwable { mThumbnail.setCancelThumbnails(true); };
		
		@Override
		public void notifyDataSetChanged() {
			//Logger.LogDebug("Data set changed.");
			try {
				//if(mFileManager != null)
				//	((OpenExplorer)getActivity()).updateTitle(mFileManager.peekStack().getPath());
				super.notifyDataSetChanged();
			} catch(NullPointerException npe) {
				Logger.LogError("Null found while notifying data change.", npe);
			}
		}
		
		private final Handler handler = new Handler(new Handler.Callback() {
			public boolean handleMessage(Message msg) {
				notifyDataSetChanged();
				return true;
			}
		});
		
		////@Override
		public View getView(int position, View view, ViewGroup parent)
		{
			final OpenPath file = super.getItem(position);
			final String mName = file.getName();
			
			int mWidth = 36, mHeight = 36;
			if(getViewMode() == OpenExplorer.VIEW_GRID)
				mWidth = mHeight = 96;
			
			if(view == null) {
				LayoutInflater in = (LayoutInflater)mContext
										.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
				
				view = in.inflate(getViewMode() == OpenExplorer.VIEW_GRID ?
							R.layout.grid_content_layout : R.layout.list_content_layout
						, parent, false);
				
				mHolder = new BookmarkHolder(file, mName, view);
				
				view.setTag(mHolder);
				
			} else {
				mHolder = (BookmarkHolder)view.getTag();
				mHolder.cancelTask();
			}

			if(getViewMode() == OpenExplorer.VIEW_LIST) {
				mHolder.setInfo(getFileDetails(file, mShowLongDate));
				
				if(file.getClass().equals(OpenMediaStore.class))
				{
					mHolder.setPath(file.getPath());
					mHolder.showPath(true);
				}
				else
					mHolder.showPath(false);
			}
			
			mHolder.setTitle(mName);
			
			ThumbnailCreator.setThumbnail(mHolder.getIconView(), file, mWidth, mHeight);

			
			return view;
		}
		
		private String getFilePath(String name) {
			return mFileManager.peekStack().getChild(name).getPath();
		}
		private String getFileDetails(OpenPath file, Boolean longDate) {
			//OpenPath file = mFileManager.peekStack().getChild(name); 
			String deets = ""; //file.getPath() + "\t\t";
			
			if(file.isDirectory() && !file.requiresThread()) {
				deets = file.list().length + " items";
			} else {
				deets = DialogHandler.formatSize(file.length());
			}
			
			deets += " | ";
			
			DateFormat df = new SimpleDateFormat(longDate ? "MM-dd-yyyy HH:mm" : "MM-dd");
			deets += df.format(file.lastModified());
			
			deets += " | ";
			
			deets += (file.isDirectory()?"d":"-");
			deets += (file.canRead()?"r":"-");
			deets += (file.canWrite()?"w":"-");
			deets += (file.canExecute()?"x":"-");
			
			return deets;
		}
		
	}
	
	public enum FileIOCommandType
	{
		ALL
	}
	public class FileIOCommand
	{
		public FileIOCommandType Type;
		public OpenPath Path;
		
		public FileIOCommand(FileIOCommandType type, OpenPath path)
		{
			Type = type;
			Path = path;
		}
	}
	
	public class FileIOTask extends AsyncTask<FileIOCommand, Integer, OpenPath[]>
	{
		@Override
		protected OpenPath[] doInBackground(FileIOCommand... params) {
			ArrayList<OpenPath> ret = new ArrayList<OpenPath>();
			for(FileIOCommand cmd : params)
			{
				if(cmd.Path.requiresThread())
				{
					OpenFTP file = (OpenFTP)FileManager.getOpenCache(cmd.Path.getPath(), true);
					OpenPath[] list = file.list();
					if(list != null)
						for(OpenPath f : list)
							ret.add(f);
				} else {
					for(OpenPath f : cmd.Path.list())
						ret.add(f);
				}
				mFileManager.pushStack(cmd.Path);
			}
			Logger.LogDebug("Found " + ret.size() + " items.");
			OpenPath[] ret2 = new OpenPath[ret.size()];
			ret.toArray(ret2);
			return ret2;
		}
		
		@Override
		protected void onPreExecute() {
			super.onPreExecute();
			//mData.clear();
		}
		
		@Override
		protected void onPostExecute(OpenPath[] result)
		{
			mData2.clear();
			updateData(result);
		}
		
	}
	public class DialogBuilder extends Builder
	{
		private View view;
		private EditText mEdit, mEdit2;
		
		public DialogBuilder(Context mContext) {
			super(mContext);
			
			LayoutInflater inflater = (LayoutInflater)mContext.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
			view = inflater.inflate(R.layout.input_dialog_layout, null);
			super.setView(view);
			mEdit = (EditText)view.findViewById(R.id.dialog_input);
			mEdit2 = (EditText)view.findViewById(R.id.dialog_input_top);
			if(mEdit == null)
				mEdit = mEdit2;
		}
		
		public String getInputText() { return mEdit.getText().toString(); }
		public DialogBuilder setPrompt(String s) {
			((EditText)view.findViewById(R.id.dialog_message_top)).setText(s);
			return this;
		}
		
		public DialogBuilder setDefaultText(String s) 
		{
			mEdit.setText(s);
			return this;
		}
		
		@Override
		public DialogBuilder setMessage(CharSequence message) {
			super.setMessage(message);
			return this;
		}
		
		@Override
		public DialogBuilder setTitle(CharSequence title) {
			super.setTitle(title);
			return this;
		}
		
		@Override
		public DialogBuilder setIcon(Drawable icon) {
			super.setIcon(icon);
			return this;
		}
	}
}

