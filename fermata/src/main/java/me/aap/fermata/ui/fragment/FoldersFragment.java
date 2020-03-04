package me.aap.fermata.ui.fragment;

import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.util.Log;

import java.util.ArrayList;
import java.util.List;

import me.aap.fermata.R;
import me.aap.fermata.media.lib.MediaLib.BrowsableItem;
import me.aap.fermata.media.lib.MediaLib.Folders;
import me.aap.fermata.media.lib.MediaLib.Item;
import me.aap.fermata.media.lib.MediaLib.PlayableItem;
import me.aap.fermata.media.pref.FoldersPrefs;
import me.aap.fermata.media.service.FermataServiceUiBinder;
import me.aap.fermata.ui.activity.MainActivityDelegate;
import me.aap.fermata.ui.view.MediaItemWrapper;
import me.aap.utils.pref.PreferenceStore;
import me.aap.utils.ui.fragment.FilePickerFragment;
import me.aap.utils.ui.menu.OverlayMenu;
import me.aap.utils.ui.menu.OverlayMenuItem;

import static android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION;
import static java.util.Objects.requireNonNull;
import static me.aap.utils.collection.CollectionUtils.filterMap;

/**
 * @author Andrey Pavlenko
 */
public class FoldersFragment extends MediaLibFragment {

	@Override
	ListAdapter createAdapter(FermataServiceUiBinder b) {
		return new FoldersAdapter(b.getLib().getFolders());
	}

	@Override
	public int getFragmentId() {
		return R.id.nav_folders;
	}

	@Override
	public CharSequence getFragmentTitle() {
		return getResources().getString(R.string.folders);
	}

	@Override
	public void initNavBarMenu(OverlayMenu menu) {
		if (isRootFolder()) {
			menu.findItem(R.id.nav_add_folder).setVisible(true);
		} else {
			FoldersAdapter a = getAdapter();
			if (!a.hasSelectable()) return;

			if (a.getListView().isSelectionActive()) {
				boolean hasSelected = a.hasSelected();

				menu.findItem(R.id.nav_select_all).setVisible(true);
				menu.findItem(R.id.nav_unselect_all).setVisible(true);

				if (hasSelected) {
					menu.findItem(R.id.nav_favorites_add).setVisible(true);
					getMainActivity().initPlaylistMenu(menu);
				}
			} else {
				menu.findItem(R.id.nav_select).setVisible(true);
			}
		}

		super.initNavBarMenu(menu);
	}

	public boolean navBarMenuItemSelected(OverlayMenuItem item) {
		switch (item.getItemId()) {
			case R.id.nav_add_folder:
				addFolder();
				return true;
			case R.id.nav_select:
			case R.id.nav_select_all:
				getAdapter().getListView().select(true);
				return true;
			case R.id.nav_unselect_all:
				getAdapter().getListView().select(false);
				return true;
			case R.id.nav_favorites_add:
				requireNonNull(getLib()).getFavorites().addItems(filterMap(getAdapter().getList(),
						MediaItemWrapper::isSelected, (i, w, l) -> l.add((PlayableItem) w.getItem()),
						ArrayList::new));
				discardSelection();
				MediaLibFragment f = getMainActivity().getMediaLibFragment(R.id.nav_favorites);
				if (f != null) f.reload();
				return true;
			case R.id.playlist_add:
				OverlayMenu menu = item.getMenu();
				getMainActivity().createPlaylistMenu(menu);
				menu.show(this::navBarMenuItemSelected);
				return true;
			case R.id.playlist_create:
				getMainActivity().createPlaylist(getAdapter().getSelectedItems(),
						getAdapter().getParent().getName());
				return true;
			case R.id.playlist_add_item:
				getMainActivity().addToPlaylist(item.getTitle().toString(), getAdapter().getSelectedItems());
				return true;
		}

		return false;
	}

	public void navBarItemReselected(int itemId) {
		getAdapter().setParent(getLib().getFolders());
	}

	public void addFolder() {
		MainActivityDelegate a = getMainActivity();

		if (!a.isCarActivity()) {
			try {
				addFolderIntent(a);
				return;
			} catch (ActivityNotFoundException ex) {
				Log.d(getClass().getName(), "Failed to start activity", ex);
			}
		}

		addFolderPicker(a);
	}

	public void addFolderIntent(MainActivityDelegate a) throws ActivityNotFoundException {
		Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
		a.startActivityForResult(this::addFolderResult, intent);
	}

	public void addFolderPicker(MainActivityDelegate a) {
		FilePickerFragment f = a.showFragment(R.id.file_picker);
		f.init(this::addFolderResult, FilePickerFragment.FOLDER);
	}

	private void addFolderResult(int result, Intent data) {
		if (data == null) return;

		Uri uri = data.getData();
		if (uri == null) return;

		requireNonNull(getMainActivity().getContext()).getContentResolver()
				.takePersistableUriPermission(uri, FLAG_GRANT_READ_URI_PERMISSION);
		Folders folders = getLib().getFolders();
		folders.addItem(uri);
		getAdapter().setParent(folders);
	}

	private void addFolderResult(Uri uri) {
		MainActivityDelegate a = getMainActivity();

		if (uri != null) {
			Folders folders = a.getLib().getFolders();
			folders.addItem(uri);
			getAdapter().setParent(folders);
		}

		a.showFragment(getFragmentId());
	}

	@Override
	protected boolean isSupportedItem(Item i) {
		return getFolders().isFoldersItemId(i.getId());
	}

	private Folders getFolders() {
		return getLib().getFolders();
	}

	private boolean isRootFolder() {
		return (getAdapter().getParent() instanceof Folders);
	}

	@Override
	public void onPreferenceChanged(PreferenceStore store, List<PreferenceStore.Pref<?>> prefs) {
		FoldersAdapter a = getAdapter();
		if (a.isCallbackCall()) return;
		if (prefs.contains(FoldersPrefs.FOLDERS) && isRootFolder()) a.reload();
		else super.onPreferenceChanged(store, prefs);
	}

	private final class FoldersAdapter extends ListAdapter {

		FoldersAdapter(BrowsableItem parent) {
			super(parent);
		}

		public boolean isLongPressDragEnabled() {
			return isRootFolder();
		}

		public boolean isItemViewSwipeEnabled() {
			return isRootFolder();
		}

		@Override
		protected void onItemDismiss(int position) {
			BrowsableItem i = getAdapter().getParent();
			if (i instanceof Folders) ((Folders) i).removeItem(position);
			super.onItemDismiss(position);
		}

		@Override
		protected boolean onItemMove(int fromPosition, int toPosition) {
			BrowsableItem i = getAdapter().getParent();
			if (i instanceof Folders) ((Folders) i).moveItem(fromPosition, toPosition);
			return super.onItemMove(fromPosition, toPosition);
		}
	}
}
