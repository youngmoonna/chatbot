package net.quber.quberchat

import android.content.Context
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import net.quber.quberchat.data.Message

class ChatAdapter(private val context: Context, private var list: MutableList<Message>) : RecyclerView.Adapter<ChatAdapter.ChatViewHolder>() {

    inner class ChatViewHolder(itemView: View?) : RecyclerView.ViewHolder(itemView!!) {
        var itemChat = itemView!!.findViewById<TextView>(R.id.item_string_text)
        var itemLinear = itemView!!.findViewById<LinearLayout>(R.id.item_string_linear)

    }

    interface OnItemClickListener{
        fun onItemClick(v: View, data: Message, pos : Int)
    }
    private var listener : OnItemClickListener? = null
    fun setOnItemClickListener(listener : OnItemClickListener) {
        this.listener = listener
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ChatViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_string, parent, false)
        return ChatViewHolder(view)
    }

    override fun onBindViewHolder(holder: ChatViewHolder, position: Int) {
        val message = list.get(position)

        when(message.role) {
            "user" -> {
                holder.itemLinear.gravity = Gravity.RIGHT
                holder.itemChat.background = context.getDrawable(R.drawable.shape_rectangle_green)
                holder.itemChat.text = String.format(context.getString(R.string.chat_string), message.role, message.content)
            }
            "model" -> {
                holder.itemLinear.gravity = Gravity.LEFT
                holder.itemChat.background = context.getDrawable(R.drawable.shape_rectangle_blue)
                holder.itemChat.text = String.format(
                    context.getString(R.string.chat_string),
                    message.role,
                    message.content
                )
            }
        }

        holder.itemView.setOnClickListener {
            listener?.onItemClick(it, message, position)
        }

    }

    override fun getItemCount(): Int {
        return list.size
    }


    fun updateItemView(position: Int) {
        notifyItemChanged(position)
    }
}