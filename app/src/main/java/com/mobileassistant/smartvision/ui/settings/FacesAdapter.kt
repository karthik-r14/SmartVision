package com.mobileassistant.smartvision.ui.settings

import android.content.Context
import android.graphics.BitmapFactory
import android.util.Base64
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.mobileassistant.smartvision.R
import com.mobileassistant.smartvision.db.FaceInfo

class FacesAdapter(
    context: Context,
    faceList: List<FaceInfo>,
    deleteClickListener: (FaceInfo) -> Unit,
    editClickListener: (FaceInfo) -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
    private var personFaceList: List<FaceInfo> = ArrayList()
    private val ctx: Context
    private var deleteButtonCLickListener: (FaceInfo) -> Unit
    private var editButtonClickListener: (FaceInfo) -> Unit

    init {
        this.personFaceList = faceList
        ctx = context
        deleteButtonCLickListener = deleteClickListener
        editButtonClickListener = editClickListener
    }

    inner class FaceItemViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        var image: ImageView
        var faceName: TextView
        var tileLayout: View
        var deleteButton: ImageButton
        var editButton: ImageButton

        init {
            image = view.findViewById(R.id.face_image) as ImageView
            faceName = view.findViewById(R.id.face_name) as TextView
            tileLayout = view.findViewById(R.id.tileLayout) as View
            deleteButton = view.findViewById(R.id.delete_image_button) as ImageButton
            editButton = view.findViewById(R.id.edit_image_button) as ImageButton
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val viewHolder: RecyclerView.ViewHolder
        val view: View =
            LayoutInflater.from(parent.context).inflate(R.layout.person_grid_card, parent, false)
        viewHolder = FaceItemViewHolder(view)
        return viewHolder
    }

    // Replace the contents of a view (invoked by the layout manager)
    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        if (holder is FaceItemViewHolder) {
            val personFace: FaceInfo = personFaceList[position]
            holder.faceName.text = personFace.faceName
            val base64 = Base64.decode(personFace.faceImage, Base64.DEFAULT)
            val bitmapImage = BitmapFactory.decodeByteArray(base64, 0, base64.size)
            holder.image.setImageBitmap(bitmapImage)
            holder.deleteButton.setOnClickListener {
                deleteButtonCLickListener(personFace)
            }
            holder.editButton.setOnClickListener {
                editButtonClickListener(personFace)
            }
        }
    }

    override fun getItemCount(): Int {
        return personFaceList.size
    }
}