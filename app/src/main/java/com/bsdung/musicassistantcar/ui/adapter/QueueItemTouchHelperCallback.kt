package com.bsdung.musicassistantcar.ui.adapter

import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import com.bsdung.musicassistantcar.playback.QueueListItem

import android.util.Log

class QueueItemTouchHelperCallback(
    private val adapter: QueueAdapter,
    private val getCurrentIndex: () -> Int,
    private val onMoveCommitted: (fromQueueIndex: Int, toQueueIndex: Int) -> Unit,
) : ItemTouchHelper.SimpleCallback(
    ItemTouchHelper.UP or ItemTouchHelper.DOWN,
    0, // No swipe actions
) {
    private var initialDragQueueIndex: Int? = null

    override fun isLongPressDragEnabled(): Boolean = true // Supports both handle drag and long-press drag

    override fun isItemViewSwipeEnabled(): Boolean = false

    override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
        // No-op: swipe actions are disabled for queue rows
    }

    override fun getMovementFlags(
        recyclerView: RecyclerView,
        viewHolder: RecyclerView.ViewHolder,
    ): Int {
        val position = viewHolder.bindingAdapterPosition
        if (position == RecyclerView.NO_POSITION) return 0
        val item = adapter.getItem(position)
        return if (item is QueueListItem.Track && item.isUpcoming) {
            makeMovementFlags(ItemTouchHelper.UP or ItemTouchHelper.DOWN, 0)
        } else {
            0
        }
    }

    override fun onMove(
        recyclerView: RecyclerView,
        viewHolder: RecyclerView.ViewHolder,
        target: RecyclerView.ViewHolder,
    ): Boolean {
        val fromPos = viewHolder.bindingAdapterPosition
        val toPos = target.bindingAdapterPosition
        if (fromPos == RecyclerView.NO_POSITION || toPos == RecyclerView.NO_POSITION) {
            return false
        }

        val fromItem = adapter.getItem(fromPos) as? QueueListItem.Track ?: return false
        val toItem = adapter.getItem(toPos) as? QueueListItem.Track ?: return false

        // Boundary check: move is permitted only between upcoming items
        if (!fromItem.isUpcoming || !toItem.isUpcoming) {
            return false
        }

        Log.i("QueueDrag", "onMove: fromPos=$fromPos, toPos=$toPos, fromIndex=${fromItem.queueIndex}, toIndex=${toItem.queueIndex}")
        adapter.moveItem(fromPos, toPos)
        return true
    }

    override fun onSelectedChanged(viewHolder: RecyclerView.ViewHolder?, actionState: Int) {
        super.onSelectedChanged(viewHolder, actionState)
        if (actionState == ItemTouchHelper.ACTION_STATE_DRAG && viewHolder != null) {
            val pos = viewHolder.bindingAdapterPosition
            if (pos != RecyclerView.NO_POSITION) {
                val item = adapter.getItem(pos) as? QueueListItem.Track
                initialDragQueueIndex = item?.queueIndex
                Log.i("QueueDrag", "drag started: pos=$pos, queueIndex=${initialDragQueueIndex}")
            }
            viewHolder.itemView.alpha = 0.85f
            viewHolder.itemView.scaleX = 1.02f
            viewHolder.itemView.scaleY = 1.02f
        }
    }

    override fun clearView(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder) {
        super.clearView(recyclerView, viewHolder)
        viewHolder.itemView.alpha = 1.0f
        viewHolder.itemView.scaleX = 1.0f
        viewHolder.itemView.scaleY = 1.0f

        val startQueueIndex = initialDragQueueIndex
        initialDragQueueIndex = null

        val finalAdapterPos = viewHolder.bindingAdapterPosition
        Log.i("QueueDrag", "clearView: startQueueIndex=$startQueueIndex, finalPos=$finalAdapterPos")
        if (startQueueIndex != null && finalAdapterPos != RecyclerView.NO_POSITION) {
            val firstUpcomingPos = adapter.getFirstUpcomingPosition()
            val currentIndex = getCurrentIndex()
            if (firstUpcomingPos != -1 && finalAdapterPos >= firstUpcomingPos) {
                val targetQueueIndex = (currentIndex + 1) + (finalAdapterPos - firstUpcomingPos)
                Log.i("QueueDrag", "clearView: targetQueueIndex=$targetQueueIndex")
                if (targetQueueIndex != startQueueIndex) {
                    onMoveCommitted(startQueueIndex, targetQueueIndex)
                }
            }
        }
    }
}
