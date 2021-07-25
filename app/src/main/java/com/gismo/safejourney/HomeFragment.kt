package com.gismo.safejourney

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.gismo.safejourney.databinding.FragmentHomeBinding

class HomeFragment: Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {

        val binding = FragmentHomeBinding.inflate(inflater, container, false)
        binding.lifecycleOwner = viewLifecycleOwner

        binding.startBikeJourney.setOnClickListener {
            binding.startBikeJourney.setBackgroundResource(R.drawable.ic_circle_shadow)
            findNavController().navigate(HomeFragmentDirections.startNavAction(false))
        }

        binding.startWalkJourney.setOnClickListener {
            binding.startWalkJourney.setBackgroundResource(R.drawable.ic_circle_shadow)
            findNavController().navigate(HomeFragmentDirections.startNavAction(true))
        }

        return binding.root
    }

}