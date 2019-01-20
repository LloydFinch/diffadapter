package com.silencedut.diffadapter.utils;

import android.os.Handler;
import android.os.Looper;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v7.recyclerview.extensions.AsyncDifferConfig;
import android.support.v7.util.AdapterListUpdateCallback;
import android.support.v7.util.DiffUtil;
import android.support.v7.util.ListUpdateCallback;
import android.support.v7.widget.RecyclerView;
import android.util.Log;

import com.silencedut.diffadapter.data.BaseMutableData;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;

/**
 *
 * @author SilenceDut
 * @date 2018/12/19
 */
public class AsyncListUpdateDiffer<T extends BaseMutableData> {
    private static final Executor DIFF_MAIN_EXECUTOR = new AsyncListUpdateDiffer.MainThreadExecutor();
    private final ListUpdateCallback mUpdateCallback;
    private final AsyncDifferConfig<T> mConfig;
    private final ListChangedCallback<T> mListChangedCallback;
    @Nullable
    private List<T> mList;

    private List<T> mCurrentList ;
    private int mMaxScheduledGeneration;


    public AsyncListUpdateDiffer(@NonNull RecyclerView.Adapter adapter, @NonNull ListChangedCallback<T> listChangedCallback, @NonNull DiffUtil.ItemCallback<T> diffCallback) {

        this.mUpdateCallback = new AdapterListUpdateCallback(adapter);
        this.mConfig = new AsyncDifferConfig.Builder<>(diffCallback).build();
        this.mListChangedCallback = listChangedCallback;
        updateCurrentList(new ArrayList<T>());
    }

    private void updateCurrentList(List<T> currentList) {
        this.mCurrentList = currentList;
        this.mListChangedCallback.onListChanged(mCurrentList);
    }


    public void submitList(@Nullable final List<T> newList) {
        final int runGeneration = ++this.mMaxScheduledGeneration;
        if (newList != this.mList) {
            if (newList == null) {
                int countRemoved = this.mList.size();
                updateOldList(null);
                updateCurrentList(new ArrayList<T>());
                this.mUpdateCallback.onRemoved(0, countRemoved);
            } else if (this.mList == null) {
                updateOldList(newList);
                updateCurrentList(new ArrayList<>(newList));
                this.mUpdateCallback.onInserted(0, newList.size());
            } else {
                final List<T> oldList = this.mList;
                Log.d("AsyncListUpdateDiffer","start:"+this.mList.size() +","+newList.size()+","+mCurrentList.size());
                this.mConfig.getBackgroundThreadExecutor().execute(new Runnable() {
                    @Override
                    public void run() {
                        final DiffUtil.DiffResult result = DiffUtil.calculateDiff(new DiffUtil.Callback() {
                            @Override
                            public int getOldListSize() {
                                return oldList.size();
                            }

                            @Override
                            public int getNewListSize() {
                                return newList.size();
                            }

                            @Override
                            public boolean areItemsTheSame(int oldItemPosition, int newItemPosition) {
                                T oldItem = oldList.get(oldItemPosition);
                                T newItem = newList.get(newItemPosition);
                                if (oldItem != null && newItem != null ) {
                                    return AsyncListUpdateDiffer.this.mConfig.getDiffCallback().areItemsTheSame(oldItem, newItem);
                                } else {
                                    return oldItem == null && newItem == null;
                                }
                            }

                            @Override
                            public boolean areContentsTheSame(int oldItemPosition, int newItemPosition) {
                                T oldItem = oldList.get(oldItemPosition);
                                T newItem = newList.get(newItemPosition);
                                if (oldItem != null && newItem != null && oldItem.getClass() == newItem.getClass()) {
                                    return AsyncListUpdateDiffer.this.mConfig.getDiffCallback().areContentsTheSame(oldItem, newItem);
                                } else if (oldItem == null && newItem == null) {
                                    return true;
                                } else {
                                    throw new AssertionError();
                                }
                            }

                            @Override
                            @Nullable
                            public Object getChangePayload(int oldItemPosition, int newItemPosition) {
                                T oldItem = oldList.get(oldItemPosition);
                                T newItem = newList.get(newItemPosition);
                                if (oldItem != null && newItem != null) {
                                    return AsyncListUpdateDiffer.this.mConfig.getDiffCallback().getChangePayload(oldItem, newItem);
                                } else {
                                    throw new AssertionError();
                                }
                            }
                        });
                        AsyncListUpdateDiffer.DIFF_MAIN_EXECUTOR.execute(new Runnable() {
                            @Override
                            public void run() {
                                if (AsyncListUpdateDiffer.this.mMaxScheduledGeneration == runGeneration) {
                                    AsyncListUpdateDiffer.this.latchList(newList, result);
                                    Log.d("AsyncListUpdateDiffer","end");
                                }

                            }
                        });
                    }
                });
            }
        }
    }

    private void latchList(@NonNull List<T> newList, @NonNull DiffUtil.DiffResult diffResult) {

        updateOldList(newList);
        updateCurrentList(new ArrayList<>(newList));
        diffResult.dispatchUpdatesTo(this.mUpdateCallback);

    }

    public void updateOldList(@Nullable List<T> newList) {
        this.mList = newList;
    }


    private static class MainThreadExecutor implements Executor {
        final Handler mHandler = new Handler(Looper.getMainLooper());

        MainThreadExecutor() {
        }

        @Override
        public void execute(@NonNull Runnable command) {
            this.mHandler.post(command);
        }
    }


}