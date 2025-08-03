package com.wlanscanner.utils

/**
 * Global WiFi Channel Mapper
 * Supports all worldwide WiFi channels across all bands and regions
 */
object ChannelMapper {
    
    data class ChannelInfo(
        val channel: Int,
        val band: String,
        val region: String = "Global"
    )
    
    /**
     * Maps frequency to channel number with global support
     * Covers all regions: US, EU, Japan, China, etc.
     */
    fun getChannelInfo(frequency: Int): ChannelInfo {
        return when (frequency) {
            // 2.4 GHz Band - Global channels 1-14
            2412 -> ChannelInfo(1, "2.4GHz")
            2417 -> ChannelInfo(2, "2.4GHz")
            2422 -> ChannelInfo(3, "2.4GHz")
            2427 -> ChannelInfo(4, "2.4GHz")
            2432 -> ChannelInfo(5, "2.4GHz")
            2437 -> ChannelInfo(6, "2.4GHz")
            2442 -> ChannelInfo(7, "2.4GHz")
            2447 -> ChannelInfo(8, "2.4GHz")
            2452 -> ChannelInfo(9, "2.4GHz")
            2457 -> ChannelInfo(10, "2.4GHz")
            2462 -> ChannelInfo(11, "2.4GHz")
            2467 -> ChannelInfo(12, "2.4GHz", "EU/JP")
            2472 -> ChannelInfo(13, "2.4GHz", "EU/JP")
            2484 -> ChannelInfo(14, "2.4GHz", "JP only")
            
            // 5 GHz Band - UNII-1 (Indoor)
            5170 -> ChannelInfo(34, "5GHz")
            5180 -> ChannelInfo(36, "5GHz")
            5190 -> ChannelInfo(38, "5GHz")
            5200 -> ChannelInfo(40, "5GHz")
            5210 -> ChannelInfo(42, "5GHz")
            5220 -> ChannelInfo(44, "5GHz")
            5230 -> ChannelInfo(46, "5GHz")
            5240 -> ChannelInfo(48, "5GHz")
            
            // 5 GHz Band - UNII-2A (Indoor/Outdoor)
            5250 -> ChannelInfo(50, "5GHz")
            5260 -> ChannelInfo(52, "5GHz")
            5270 -> ChannelInfo(54, "5GHz")
            5280 -> ChannelInfo(56, "5GHz")
            5290 -> ChannelInfo(58, "5GHz")
            5300 -> ChannelInfo(60, "5GHz")
            5310 -> ChannelInfo(62, "5GHz")
            5320 -> ChannelInfo(64, "5GHz")
            
            // 5 GHz Band - UNII-2C (Outdoor)
            5500 -> ChannelInfo(100, "5GHz")
            5510 -> ChannelInfo(102, "5GHz")
            5520 -> ChannelInfo(104, "5GHz")
            5530 -> ChannelInfo(106, "5GHz")
            5540 -> ChannelInfo(108, "5GHz")
            5550 -> ChannelInfo(110, "5GHz")
            5560 -> ChannelInfo(112, "5GHz")
            5570 -> ChannelInfo(114, "5GHz")
            5580 -> ChannelInfo(116, "5GHz")
            5590 -> ChannelInfo(118, "5GHz")
            5600 -> ChannelInfo(120, "5GHz")
            5610 -> ChannelInfo(122, "5GHz")
            5620 -> ChannelInfo(124, "5GHz")
            5630 -> ChannelInfo(126, "5GHz")
            5640 -> ChannelInfo(128, "5GHz")
            5660 -> ChannelInfo(132, "5GHz")
            5670 -> ChannelInfo(134, "5GHz")
            5680 -> ChannelInfo(136, "5GHz")
            5690 -> ChannelInfo(138, "5GHz")
            5700 -> ChannelInfo(140, "5GHz")
            5710 -> ChannelInfo(142, "5GHz")
            5720 -> ChannelInfo(144, "5GHz")
            
            // 5 GHz Band - UNII-3 (Outdoor)
            5745 -> ChannelInfo(149, "5GHz")
            5755 -> ChannelInfo(151, "5GHz")
            5765 -> ChannelInfo(153, "5GHz")
            5775 -> ChannelInfo(155, "5GHz")
            5785 -> ChannelInfo(157, "5GHz")
            5795 -> ChannelInfo(159, "5GHz")
            5805 -> ChannelInfo(161, "5GHz")
            5815 -> ChannelInfo(163, "5GHz")
            5825 -> ChannelInfo(165, "5GHz")
            5835 -> ChannelInfo(167, "5GHz")
            5845 -> ChannelInfo(169, "5GHz")
            5855 -> ChannelInfo(171, "5GHz")
            5865 -> ChannelInfo(173, "5GHz")
            5875 -> ChannelInfo(175, "5GHz")
            5885 -> ChannelInfo(177, "5GHz")
            5895 -> ChannelInfo(179, "5GHz")
            5905 -> ChannelInfo(181, "5GHz")
            
            // 6 GHz Band - WiFi 6E (UNII-5, UNII-6, UNII-7, UNII-8)
            5925 -> ChannelInfo(1, "6GHz")
            5935 -> ChannelInfo(5, "6GHz")
            5945 -> ChannelInfo(9, "6GHz")
            5955 -> ChannelInfo(13, "6GHz")
            5965 -> ChannelInfo(17, "6GHz")
            5975 -> ChannelInfo(21, "6GHz")
            5985 -> ChannelInfo(25, "6GHz")
            5995 -> ChannelInfo(29, "6GHz")
            6005 -> ChannelInfo(33, "6GHz")
            6015 -> ChannelInfo(37, "6GHz")
            6025 -> ChannelInfo(41, "6GHz")
            6035 -> ChannelInfo(45, "6GHz")
            6045 -> ChannelInfo(49, "6GHz")
            6055 -> ChannelInfo(53, "6GHz")
            6065 -> ChannelInfo(57, "6GHz")
            6075 -> ChannelInfo(61, "6GHz")
            6085 -> ChannelInfo(65, "6GHz")
            6095 -> ChannelInfo(69, "6GHz")
            6105 -> ChannelInfo(73, "6GHz")
            6115 -> ChannelInfo(77, "6GHz")
            6125 -> ChannelInfo(81, "6GHz")
            6135 -> ChannelInfo(85, "6GHz")
            6145 -> ChannelInfo(89, "6GHz")
            6155 -> ChannelInfo(93, "6GHz")
            6165 -> ChannelInfo(97, "6GHz")
            6175 -> ChannelInfo(101, "6GHz")
            6185 -> ChannelInfo(105, "6GHz")
            6195 -> ChannelInfo(109, "6GHz")
            6205 -> ChannelInfo(113, "6GHz")
            6215 -> ChannelInfo(117, "6GHz")
            6225 -> ChannelInfo(121, "6GHz")
            6235 -> ChannelInfo(125, "6GHz")
            6245 -> ChannelInfo(129, "6GHz")
            6255 -> ChannelInfo(133, "6GHz")
            6265 -> ChannelInfo(137, "6GHz")
            6275 -> ChannelInfo(141, "6GHz")
            6285 -> ChannelInfo(145, "6GHz")
            6295 -> ChannelInfo(149, "6GHz")
            6305 -> ChannelInfo(153, "6GHz")
            6315 -> ChannelInfo(157, "6GHz")
            6325 -> ChannelInfo(161, "6GHz")
            6335 -> ChannelInfo(165, "6GHz")
            6345 -> ChannelInfo(169, "6GHz")
            6355 -> ChannelInfo(173, "6GHz")
            6365 -> ChannelInfo(177, "6GHz")
            6375 -> ChannelInfo(181, "6GHz")
            6385 -> ChannelInfo(185, "6GHz")
            6395 -> ChannelInfo(189, "6GHz")
            6405 -> ChannelInfo(193, "6GHz")
            6415 -> ChannelInfo(197, "6GHz")
            6425 -> ChannelInfo(201, "6GHz")
            6435 -> ChannelInfo(205, "6GHz")
            6445 -> ChannelInfo(209, "6GHz")
            6455 -> ChannelInfo(213, "6GHz")
            6465 -> ChannelInfo(217, "6GHz")
            6475 -> ChannelInfo(221, "6GHz")
            6485 -> ChannelInfo(225, "6GHz")
            6495 -> ChannelInfo(229, "6GHz")
            6505 -> ChannelInfo(233, "6GHz")
            
            // 60 GHz Band - 802.11ad/ay (WiGig)
            57240 -> ChannelInfo(1, "60GHz")
            58320 -> ChannelInfo(2, "60GHz")
            59400 -> ChannelInfo(3, "60GHz")
            60480 -> ChannelInfo(4, "60GHz")
            61560 -> ChannelInfo(5, "60GHz")
            62640 -> ChannelInfo(6, "60GHz")
            63720 -> ChannelInfo(7, "60GHz")
            64800 -> ChannelInfo(8, "60GHz")
            65880 -> ChannelInfo(9, "60GHz")
            66960 -> ChannelInfo(10, "60GHz")
            68040 -> ChannelInfo(11, "60GHz")
            69120 -> ChannelInfo(12, "60GHz")
            70200 -> ChannelInfo(13, "60GHz")
            
            else -> {
                // Fallback for unknown frequencies
                when {
                    frequency in 2400..2500 -> {
                        // Calculate 2.4GHz channel for edge cases
                        val calculatedChannel = when {
                            frequency <= 2484 -> ((frequency - 2412) / 5) + 1
                            else -> 0
                        }
                        ChannelInfo(calculatedChannel, "2.4GHz", "Calc")
                    }
                    frequency in 5000..5900 -> {
                        // Calculate 5GHz channel for edge cases
                        val calculatedChannel = (frequency - 5000) / 5
                        ChannelInfo(calculatedChannel, "5GHz", "Calc")
                    }
                    frequency in 5900..6500 -> {
                        // Calculate 6GHz channel for edge cases
                        val calculatedChannel = (frequency - 5925) / 5 + 1
                        ChannelInfo(calculatedChannel, "6GHz", "Calc")
                    }
                    frequency in 57000..71000 -> {
                        // Calculate 60GHz channel for edge cases
                        val calculatedChannel = (frequency - 57240) / 1080 + 1
                        ChannelInfo(calculatedChannel, "60GHz", "Calc")
                    }
                    else -> ChannelInfo(0, "Unknown")
                }
            }
        }
    }
    
    /**
     * Get all supported frequency bands
     */
    fun getSupportedBands(): List<String> {
        return listOf("2.4GHz", "5GHz", "6GHz", "60GHz")
    }
    
    /**
     * Check if frequency is in a specific band
     */
    fun isInBand(frequency: Int, band: String): Boolean {
        return when (band) {
            "2.4GHz" -> frequency in 2412..2484
            "5GHz" -> frequency in 5170..5905
            "6GHz" -> frequency in 5925..6505
            "60GHz" -> frequency in 57240..70200
            else -> false
        }
    }
}
