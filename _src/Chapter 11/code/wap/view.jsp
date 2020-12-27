<div data-role="page" id="home">
  <div data-role="header" data-theme="b"><h2><a data-ajax="false" href="#"></a>Action List</h2></div>
  <div data-role="content">
   <ul data-role="listview">
   <li class="view-home"><a href="#view-home">Map</a></li>
   <li><a href="#details-home">Details</a></li> 
   <li><a href="#q-and-a-home">Question and Answer</a></li>
   </ul>
 </div>
</div>

<div data-role="page" class="page-map" id="view-home" style="width:100%; height:100%;">
  <div data-role="header" data-position="fixed" data-theme="b"><h2>Map</h2></div>
  <div data-role="content" style="width:100%; height:240px; padding:0;">
    <div id="map_canvasRoast" style="width:100%; height:240px;"> </div>
  </div>
</div>

<script type="text/javascript" src="http://maps.google.com/maps/api/js?sensor=false"></script>

<script type="text/javascript">

var center;

var map = null;

function Newinitialize(lat,lng) {
	center = new google.maps.LatLng(lat,lng);
	var myOptions = {
		zoom: 14,
		center: center,
		mapTypeId: google.maps.MapTypeId.ROADMAP
	}
	map = new google.maps.Map(document.getElementById("map_canvasRoast"), myOptions);
}

function detectBrowser() {
	var useragent = navigator.userAgent;
	var mapdivMap = document.getElementById("map_canvasRoast");
	
	if (useragent.indexOf('iPhone') != -1 || useragent.indexOf('Android') != -1 ) {
		mapdivMap.style.width = '100%';
		mapdivMap.style.height = '100%';
	} else {
		mapdivMap.style.width = '100%';
		mapdivMap.style.height = '240px';
	}
	};

$('.view-home').live('click', function() {
	if(navigator.geolocation) {
		detectBrowser();
		navigator.geolocation.getCurrentPosition(function(position){
				Newinitialize(position.coords.latitude,position.coords.longitude);
			});
		} else {
		detectBrowser();
		Newinitialize(52.636161,-1.133065);
	}
});
</script>