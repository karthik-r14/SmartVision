package com.mobileassistant.smartvision.ui.home

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.GridLayoutManager
import com.mobileassistant.smartvision.R
import com.mobileassistant.smartvision.databinding.FragmentHomeBinding
import com.mobileassistant.smartvision.model.MenuItem

private const val NO_OF_COLUMNS = 2

class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null

    // This property is only valid between onCreateView and
    // onDestroyView.
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        val homeViewModel = ViewModelProvider(this)[HomeViewModel::class.java]

        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        val root: View = binding.root

        setupUi()
//        homeViewModel.text.observe(viewLifecycleOwner) {
//            textView.text = it
//        }
        return root
    }

    private fun setupUi() {
        with(binding.recyclerView) {
            layoutManager = GridLayoutManager(context, NO_OF_COLUMNS)
            val items: List<MenuItem> = listOf(
                MenuItem(getString(R.string.menu_item_1), R.drawable.reading_mode),
                MenuItem(getString(R.string.menu_item_2), R.drawable.smart_cap),
                MenuItem(getString(R.string.menu_item_3), R.drawable.face_recognition),
                MenuItem(getString(R.string.menu_item_4), R.drawable.barcode_code_scanner)
            )
            val adapter = DashboardAdapter(context, items)
            setAdapter(adapter)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}