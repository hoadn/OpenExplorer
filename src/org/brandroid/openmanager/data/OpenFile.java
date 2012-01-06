package org.brandroid.openmanager.data;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.ref.SoftReference;
import java.util.HashSet;
import java.util.Hashtable;

import org.brandroid.openmanager.util.DFInfo;
import org.brandroid.openmanager.util.RootManager;
import org.brandroid.openmanager.util.ThumbnailCreator;
import org.brandroid.utils.Logger;

import android.app.AlertDialog.Builder;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.StatFs;
import android.text.format.Formatter;

public class OpenFile extends OpenPath
{
	private File mFile;
	private OpenFile[] mChildren = null;
	private boolean bGrandPeeked = false;
	
	public OpenFile(File f) { mFile = f; }
	public OpenFile(String path) { mFile = new File(path); }
	
	public File getFile() { return mFile; }

	@Override
	public String getName() {
		return mFile.getName();
	}

	@Override
	public String getPath() {
		return mFile.getPath();
	}

	@Override
	public long length() {
		return mFile.length();
	}
	
	@Override
	public int getListLength() {
		if(mChildren != null)
			return mChildren.length;
		else return -1;
	}
	
	public long getFreeSpace() {
		try {
			StatFs stat = new StatFs(getPath());
			if(stat.getFreeBlocks() > 0)
				return (long)stat.getFreeBlocks() * (long)stat.getBlockSize();
		} catch(Exception e) {
			Logger.LogWarning("Couldn't get Total Space.", e);
		}
		if(DFInfo.LoadDF().containsKey(getPath()))
			return (long)DFInfo.LoadDF().get(getPath()).getFree();
		return Build.VERSION.SDK_INT > 8 ? mFile.getFreeSpace() * 1024 * 1024 : 0;
	}
	public long getUsableSpace() {
		try {
			StatFs stat = new StatFs(getPath());
			if(stat.getAvailableBlocks() > 0)
				return (long)stat.getAvailableBlocks() * (long)stat.getBlockSize();
		} catch(Exception e) {
			Logger.LogWarning("Couldn't get Total Space.", e);
		}
		if(DFInfo.LoadDF().containsKey(getPath()))
			return (long)(DFInfo.LoadDF().get(getPath()).getSize() - DFInfo.LoadDF().get(getPath()).getFree());
		return mFile.getUsableSpace();
	}
	public long getTotalSpace() {
		try {
			StatFs stat = new StatFs(getPath());
			if(stat.getBlockCount() > 0)
				return (long)stat.getBlockCount() * (long)stat.getBlockSize();
		} catch(Exception e) {
			Logger.LogWarning("Couldn't get Total Space for " + getPath(), e);
		}
		if(DFInfo.LoadDF().containsKey(getPath()))
			return (long)DFInfo.LoadDF().get(getPath()).getSize();
		return Build.VERSION.SDK_INT > 8 ? mFile.getTotalSpace() * 1024 * 1024 : length();
	}
	public long getUsedSpace() {
		long ret = length();
		if(isDirectory())
			for(OpenPath kid : list())
				ret += ((OpenFile)kid).getUsedSpace();
		return ret;
	}
	public int countAllFiles() {
		if(!isDirectory()) return 1;
		int ret = 0;
		for(OpenPath kid : list())
			ret += ((OpenFile)kid).countAllFiles();
		return ret;
	}
	public int countAllDirectories() {
		if(!isDirectory()) return 0;
		int ret = 1;
		for(OpenPath kid : list())
			ret += ((OpenFile)kid).countAllFiles();
		return ret;
	}

	@Override
	public OpenPath getParent() {
		if(mFile.getParent() == null)
			return null;
		return new OpenFile(mFile.getParent());
	}

	@Override
	public OpenPath[] listFiles() { return listFiles(false); }
	
	public OpenPath[] listFiles(boolean grandPeek) {
		if(mChildren == null)
		{
			File[] arr = mFile.listFiles();
			if((arr == null || arr.length == 0) && isDirectory())
			{
				if(RootManager.Default.isRoot() && (mFile.getName().equalsIgnoreCase("data") || mFile.getPath().indexOf("/data") > -1 || mFile.getPath().indexOf("/system") > -1))
				{
					Logger.LogDebug("Trying to list " + mFile.getPath() + " via Su");
					HashSet<String> files = RootManager.Default.execute("ls " + mFile.getPath());
					if(files != null)
					{
						mChildren = new OpenFile[files.size()];
						int i = 0;
						for(String s : files)
						{
							mChildren[i++] = new OpenFile(getPath() + "/" + s);
							if(grandPeek && mChildren[i].isDirectory())
								mChildren[i++].listFiles();
						}
						return mChildren;
					}
				}
			}
			if((arr == null || arr.length == 0) && !isDirectory() && mFile.getParentFile() != null)
				arr = mFile.getParentFile().listFiles();
			
			if(arr == null)
				return new OpenFile[0];
			
			mChildren = new OpenFile[arr.length];
			for(int i = 0; i < arr.length; i++)
				mChildren[i] = new OpenFile(arr[i]);
		}

		if(grandPeek && !bGrandPeeked && mChildren != null && mChildren.length > 0)
		{
			for(int i=0; i < mChildren.length; i++)
			{
				if(!mChildren[i].isDirectory()) continue;
				mChildren[i].listFiles();
			}
			bGrandPeeked = true;
		}

		return mChildren;
	}

	@Override
	public Boolean isDirectory() {
		return mFile.isDirectory();
	}

	@Override
	public Uri getUri() {
		return Uri.fromFile(mFile);
	}

	@Override
	public Long lastModified() {
		return mFile.lastModified();
	}

	@Override
	public Boolean canRead() {
		return mFile.canRead();
	}

	@Override
	public Boolean canWrite() {
		return mFile.canWrite();
	}

	@Override
	public Boolean canExecute() {
		if(Build.VERSION.SDK_INT > 9)
			return mFile.canExecute();
		else return false;
	}

	@Override
	public Boolean exists() {
		return mFile.exists();
	}
	@Override
	public OpenPath[] list() {
		return listFiles();
	}
	
	@Override
	public Boolean requiresThread() {
		return false;
	}
	@Override
	public String getAbsolutePath() {
		return mFile.getAbsolutePath();
	}
	@Override
	public OpenPath getChild(String name)
	{
		File base = getFile();
		if(!base.isDirectory())
			base = base.getParentFile();
		return new OpenFile(new File(base, name));
	}
	@Override
	public Boolean isFile() {
		return mFile.isFile();
	}
	@Override
	public Boolean delete() {
		return mFile.delete();
	}
	@Override
	public Boolean mkdir() {
		return mFile.mkdirs();
	}
	@Override
	public InputStream getInputStream() throws IOException {
		return new FileInputStream(mFile);
	}
	@Override
	public OutputStream getOutputStream() throws IOException {
		return new FileOutputStream(mFile);
	}
	@Override
	public Boolean isHidden() {
		return mFile.isHidden() || mFile.getName().startsWith(".");
	}
	
	@Override
	public void setPath(String path) {
		mFile = new File(path); 
	}
	

	
}
