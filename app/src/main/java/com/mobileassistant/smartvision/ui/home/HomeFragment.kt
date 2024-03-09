package com.mobileassistant.smartvision.ui.home

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import android.widget.Toast.LENGTH_LONG
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
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

        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        val root: View = binding.root

        setupUi()
        return root
    }

    private fun setupUi() {
        with(binding.recyclerView) {
            layoutManager = GridLayoutManager(context, NO_OF_COLUMNS)
            val items: List<MenuItem> = listOf(
                MenuItem(
                    getString(R.string.menu_item_1),
                    R.drawable.reading_mode,
                    ::navigateToReadingMode
                ), MenuItem(
                    getString(R.string.menu_item_2),
                    R.drawable.object_detection_icon,
                    ::navigateToSmartCap
                ), MenuItem(
                    getString(R.string.menu_item_3),
                    R.drawable.face_recognition,
                    ::navigateToDetectFaces
                ), MenuItem(
                    getString(R.string.menu_item_4),
                    R.drawable.barcode_code_scanner,
                    ::navigateToBarcodeScanner
                )
            )
            val adapter = DashboardAdapter(context, items)
            setAdapter(adapter)
        }
    }

    private fun navigateToReadingMode() = findNavController().navigate(R.id.nav_reading_mode)
    private fun navigateToSmartCap() = findNavController().navigate(R.id.nav_object_detection)

    private fun navigateToDetectFaces() = findNavController().navigate(R.id.nav_face_detection)
    private fun navigateToBarcodeScanner() = findNavController().navigate(R.id.nav_scan_code)

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}