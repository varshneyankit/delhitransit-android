package com.delhitransit.delhitransit_android.fragment;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.provider.Settings;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.style.StyleSpan;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.cardview.widget.CardView;
import androidx.core.app.ActivityCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.arlib.floatingsearchview.FloatingSearchView;
import com.arlib.floatingsearchview.suggestions.model.SearchSuggestion;
import com.delhitransit.delhitransit_android.R;
import com.delhitransit.delhitransit_android.adapter.RoutesListAdapter;
import com.delhitransit.delhitransit_android.api.ApiClient;
import com.delhitransit.delhitransit_android.api.ApiInterface;
import com.delhitransit.delhitransit_android.helperclasses.BusStopsSuggestion;
import com.delhitransit.delhitransit_android.helperclasses.CircleMarker;
import com.delhitransit.delhitransit_android.helperclasses.MarkerDetails;
import com.delhitransit.delhitransit_android.helperclasses.TimeConverter;
import com.delhitransit.delhitransit_android.helperclasses.ViewMarker;
import com.delhitransit.delhitransit_android.pojos.route.CustomizeRouteDetail;
import com.delhitransit.delhitransit_android.pojos.route.RouteDetailForAdapter;
import com.delhitransit.delhitransit_android.pojos.stops.StopDetail;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.LatLngBounds;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.Polyline;
import com.google.android.gms.maps.model.PolylineOptions;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.snackbar.Snackbar;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;

import jp.wasabeef.blurry.Blurry;
import me.zhanghai.android.materialprogressbar.MaterialProgressBar;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

import static android.view.WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS;

public class MapsFragment extends Fragment {

    private static final String TAG = MapsFragment.class.getSimpleName();
    private static final int WINDOW_DECORATION_FLAG = FLAG_TRANSLUCENT_STATUS;
    private final int LOCATION_PERMISSION_REQUEST_CODE = 101;
    private final int LOCATION_ON_REQUEST_CODE = 101;
    private final List<RouteDetailForAdapter> routesList = new ArrayList<>();
    private GoogleMap mMap;
    private Polyline currentPolyline;
    private ApiInterface apiService;
    private FloatingSearchView searchView1, searchView2;
    private CardView progressCardView;
    private Button bottomButton;
    private BottomSheetDialog routesBottomSheetDialog;
    private RecyclerView routesListRecycleView;
    private RoutesListAdapter routesListAdapter;
    private String currQuery = "";
    private MarkerDetails sourceMarkerDetail, destinationMarkerDetail;
    private LatLng userLocation;
    private HashMap<Marker, StopDetail> busStopsHashMap = new HashMap<>();
    private TextView noRoutesAvailableTextView;
    private View parentView;
    private ImageView blurView;
    private Context context;
    private MaterialProgressBar horizontalProgressBar;
    private final OnMapReadyCallback callback = new OnMapReadyCallback() {

        @Override
        public void onMapReady(GoogleMap googleMap) {
            mMap = googleMap;

            progressBarVisibility(false);
            viewVisibility(searchView1, true);
            LatLng latLng = new LatLng(28.6172368, 77.2059964);
            mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(latLng, 12));

            mMap.setPadding(100, 600, 100, 100);
            mMap.setOnMarkerClickListener(marker -> {
                StopDetail stop = busStopsHashMap.get(marker);
                if (stop != null) {
                    Runnable runnable = () -> setStopDataOnSearchView(stop, searchView1, false);
                    Activity activity = getActivity();
                    if (activity instanceof OnStopMarkerClickListener) {
                        ((OnStopMarkerClickListener) activity).onStopMarkerClicked(stop, runnable);
                    } else runnable.run();
                }
                /*if (busStopsHashMap.containsKey(marker)) {
                    setStopDataOnSearchView(busStopsHashMap.get(marker), searchView1, false);
                }*/
                /*
                    StopDetail stop = nearByBusStopsHashMap.get(marker);
                    Runnable runnable = () -> setStopDataOnSearchView(stop, searchView1, false);
                    Activity activity = getActivity();
                    if (activity instanceof OnStopMarkerClickListener) {
                        ((OnStopMarkerClickListener) activity).onStopMarkerClicked(stop, runnable);
                    } else runnable.run();
                    */
                return true;
            });
            getUserLocation();

        }

    };
    private CircleMarker circleMarker;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        parentView = inflater.inflate(R.layout.fragment_map, container, false);
        context = this.getContext();
        apiService = ApiClient.getApiService(context);
        setMapFragment();
        init();

        return parentView;
    }

    private void setMapFragment() {
        SupportMapFragment mapFragment = (SupportMapFragment) getChildFragmentManager().findFragmentById(R.id.map);
        if (mapFragment != null) {
            mapFragment.getMapAsync(callback);
        }
    }

    private void init() {
        searchView1 = parentView.findViewById(R.id.floating_bus_stop_search_view_1);
        searchView2 = parentView.findViewById(R.id.floating_bus_stop_search_view_2);
        bottomButton = parentView.findViewById(R.id.bottom_button);
        progressCardView = parentView.findViewById(R.id.progress_bar);
        blurView = parentView.findViewById(R.id.blur_view);
        horizontalProgressBar = parentView.findViewById(R.id.horizontal_loading_bar);

        viewVisibility(searchView1, false);
        viewVisibility(searchView2, false);
        viewVisibility(bottomButton, false);

        setSearchViewQueryAndSearchListener(searchView1, false);
        setSearchViewQueryAndSearchListener(searchView2, true);
        setRoutesBottomSheetDialog();

        bottomButton.setOnClickListener(this::showRoutesBottomSheet);
    }

    private void setRoutesBottomSheetDialog() {
        routesBottomSheetDialog = new BottomSheetDialog(context, R.style.BottomSheetDialogTheme);
        routesBottomSheetDialog.setContentView(getLayoutInflater().inflate(R.layout.routes_bottom_sheet_view, null));

        routesListRecycleView = routesBottomSheetDialog.findViewById(R.id.routes_list_recycle_view);
        noRoutesAvailableTextView = routesBottomSheetDialog.findViewById(R.id.no_routes_available_text_view);

        routesListAdapter = new RoutesListAdapter(context, routesList, this::onRouteSelected, this::onTaskDone);

        routesListRecycleView.setLayoutManager(new LinearLayoutManager(context, RecyclerView.VERTICAL, false));
        routesListRecycleView.setAdapter(routesListAdapter);

    }

    private void onRouteSelected() {
        progressBarVisibility(true);
        routesBottomSheetDialog.dismiss();
    }

    private void onTaskDone(Object[] values) {
        if (!(values[0] instanceof Boolean)) {
            if (currentPolyline != null) {
                currentPolyline.remove();
                circleMarker.remove();
            }
            currentPolyline = mMap.addPolyline((PolylineOptions) values[0]);
            circleMarker = new CircleMarker(mMap, context, currentPolyline);
        } else {
            routesBottomSheetDialog.dismiss();
            showToast("Route plotting not available for this trip");
        }
        mMap.animateCamera(CameraUpdateFactory.newLatLngBounds(new LatLngBounds.Builder().include(sourceMarkerDetail.latLng).include(destinationMarkerDetail.latLng).build(), 0));
        progressBarVisibility(false);
    }

    private void viewVisibility(View view, boolean visible) {
        view.setVisibility(visible ? View.VISIBLE : View.GONE);
    }

    private void progressBarVisibility(boolean visible) {
        if (visible) {
            horizontalProgressBar.setVisibility(View.GONE);
            mMap.snapshot(bitmap -> {
                blurView.setVisibility(View.VISIBLE);
                blurView.setImageBitmap(bitmap);

                Blurry.with(context)
                        .radius(15)
                        .sampling(2)
                        .onto(parentView.findViewById(R.id.main_layout));

                viewVisibility(progressCardView, true);
            });

        } else {
            blurView.setVisibility(View.GONE);
            Blurry.delete(parentView.findViewById(R.id.main_layout));
            viewVisibility(progressCardView, false);
        }
    }

    private void setSearchViewQueryAndSearchListener(FloatingSearchView searchView, boolean isSecondSearchView) {
        searchView.setOnQueryChangeListener((oldQuery, newQuery) -> {
            if (!isSecondSearchView) {
                viewVisibility(searchView2, false);
            }
            if (newQuery.equals("")) {
                searchView.clearSuggestions();
            } else if (!newQuery.trim().equals("")) {
                currQuery = newQuery;
                searchView.showProgress();
                apiService.getStopsByName(newQuery, false).enqueue(new Callback<List<StopDetail>>() {
                    @Override
                    public void onResponse(Call<List<StopDetail>> call, Response<List<StopDetail>> response) {
                        if (response.body() != null) {
                            List<BusStopsSuggestion> busStopsSuggestions = new ArrayList<>();
                            for (StopDetail stopsResponseData : response.body()) {
                                busStopsSuggestions.add(new BusStopsSuggestion(stopsResponseData));
                            }
                            searchView.swapSuggestions(busStopsSuggestions);
                        }
                        searchView.hideProgress();
                    }

                    @Override
                    public void onFailure(Call<List<StopDetail>> call, Throwable t) {
                        Log.e(TAG, "onFailure: int " + t.getMessage());
                        searchView.hideProgress();
                    }
                });

            }
        });
        searchView.setOnSearchListener(new FloatingSearchView.OnSearchListener() {
            @Override
            public void onSuggestionClicked(SearchSuggestion searchSuggestion) {
                StopDetail stopsDetail = ((BusStopsSuggestion) searchSuggestion).getStopDetail();
                setStopDataOnSearchView(stopsDetail, searchView, isSecondSearchView);
            }

            @Override
            public void onSearchAction(String currentQuery) {
                apiService.getStopsByName(currentQuery, true).enqueue(new Callback<List<StopDetail>>() {
                    @Override
                    public void onResponse(Call<List<StopDetail>> call, Response<List<StopDetail>> response) {

                        if (response.body() != null && response.body().size() != 0) {
                            setStopDataOnSearchView(response.body().get(0), searchView, isSecondSearchView);
                        } else {
                            showToast("Sorry ,No bus stop with \"" + currentQuery + "\" found");
                        }
                    }

                    @Override
                    public void onFailure(Call<List<StopDetail>> call, Throwable t) {
                        Log.e(TAG, "onFailure: int " + t.getMessage());
                    }
                });

            }
        });
        searchView.setOnBindSuggestionCallback((View suggestionView, ImageView leftIcon, TextView textView, SearchSuggestion item, int itemPosition) -> {
            String temp = item.getBody();
            SpannableStringBuilder content = new SpannableStringBuilder(temp);
            if (temp.toLowerCase().contains(currQuery.toLowerCase())) {
                int index = temp.toLowerCase().indexOf(currQuery.toLowerCase());
                content.setSpan(
                        new StyleSpan(Typeface.BOLD),
                        index,
                        index + currQuery.length(),
                        Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                );
            }
            textView.setTextColor(context.getColor(R.color.black));
            textView.setText(content);
        });
    }

    private void setStopDataOnSearchView(StopDetail stopsDetail, FloatingSearchView searchView, boolean isSecondSearchView) {

        MarkerDetails markerDetails = new MarkerDetails(stopsDetail, isSecondSearchView);
        mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(markerDetails.latLng, 17));
        addMarkerIfNotNull(markerDetails);

        if (isSecondSearchView) {
            if (destinationMarkerDetail != null) {
                destinationMarkerDetail.remove();
            }
            destinationMarkerDetail = markerDetails;
            searchView2.clearSearchFocus();
            progressBarVisibility(true);

            apiService.getCustomizeRoutesBetweenStops(destinationMarkerDetail.id, sourceMarkerDetail.id, ((int) TimeConverter.getSecondsSince12AM())).enqueue(new Callback<List<CustomizeRouteDetail>>() {
                @Override
                public void onResponse(Call<List<CustomizeRouteDetail>> call, Response<List<CustomizeRouteDetail>> response) {
                    if (response.body() != null && !response.body().isEmpty()) {
                        routesList.clear();
                        routesListAdapter.setDetail(sourceMarkerDetail.latLng, destinationMarkerDetail.latLng, sourceMarkerDetail.name);
                        routesList.addAll(makeListAdapter(response.body()));
                        routesListAdapter.notifyDataSetChanged();

                        viewVisibility(noRoutesAvailableTextView, false);
                        viewVisibility(routesListRecycleView, true);
                    } else {
                        viewVisibility(routesListRecycleView, false);
                        viewVisibility(noRoutesAvailableTextView, true);
                    }

                    routesBottomSheetDialog.show();
                    progressBarVisibility(false);
                    viewVisibility(bottomButton, true);
                }

                @Override
                public void onFailure(Call<List<CustomizeRouteDetail>> call, Throwable t) {

                }
            });
        } else {
            if (sourceMarkerDetail != null) {
                sourceMarkerDetail.remove();
            }
            sourceMarkerDetail = markerDetails;

            searchView1.clearSearchFocus();
            viewVisibility(searchView2, true);
            searchView2.setSearchFocused(true);
        }
        searchView.setSearchText(stopsDetail.getName());
    }

    private List<RouteDetailForAdapter> makeListAdapter(List<CustomizeRouteDetail> customizeRouteDetailList) {
        List<RouteDetailForAdapter> list = new ArrayList<>();
        for (CustomizeRouteDetail customizeRouteDetail : customizeRouteDetailList) {
            for (String busTiming : customizeRouteDetail.getBusTimings()) {
                list.add(new RouteDetailForAdapter(customizeRouteDetail.getTravelTime(),
                        customizeRouteDetail.getRouteId(),
                        customizeRouteDetail.getTripId(),
                        TimeConverter.getTimeInSeconds(busTiming),
                        customizeRouteDetail.getLongName()));
            }
        }
        list.sort(Comparator.comparingLong(RouteDetailForAdapter::getBusTimings));
        return list;
    }


    private void addMarkerIfNotNull(MarkerDetails markerDetail) {
        if (markerDetail.latLng != null) {
            busStopsHashMap.remove(markerDetail.marker);
            Marker marker = mMap.addMarker(new MarkerOptions().icon(BitmapDescriptorFactory.fromBitmap(new ViewMarker(context, markerDetail.name, markerDetail.relation).getBitmap())).position(markerDetail.latLng));
            busStopsHashMap.put(marker, markerDetail.stopsResponseData);
        }
    }

    //TODO fix??
    @Override
    public void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == LOCATION_ON_REQUEST_CODE) {
            getUserLocation();
        }
    }

    @Override
    public void
    onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == LOCATION_PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                getUserLocation();
            } else {
                Toast.makeText(context, "Permission Denied", Toast.LENGTH_LONG).show();
            }
        }
    }

    private void getUserLocation() {
        if (checkPermissions()) {
            LocationManager locationManager = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
            if (isLocationEnabled(locationManager)) {
                try {
                    horizontalProgressBar.setVisibility(View.VISIBLE);
                    locationManager.requestSingleUpdate("fused", new LocationListener() {
                        @Override
                        public void onLocationChanged(Location location) {
                            horizontalProgressBar.setVisibility(View.GONE);
                            userLocation = new LatLng(location.getLatitude(), location.getLongitude());
                            setUserLocation();
                            setNearByBusStopsWithInDistance(userLocation.latitude, userLocation.longitude, 1);
                        }

                        @Override
                        public void onStatusChanged(String provider, int status, Bundle extras) {

                        }

                        @Override
                        public void onProviderEnabled(String provider) {

                        }

                        @Override
                        public void onProviderDisabled(String provider) {

                        }

                    }, null);
                } catch (SecurityException e) {
                    Log.e(TAG, "getLastLocation: " + e.getMessage());
                    e.printStackTrace();
                    horizontalProgressBar.setVisibility(View.GONE);
                }
            } else {
                Snackbar.make(parentView.findViewById(R.id.map), "Please turn on your location", Snackbar.LENGTH_LONG)
                        .setAction("TURN ON", v -> {
                            Intent intent = new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS);
                            startActivityForResult(intent, LOCATION_ON_REQUEST_CODE);
                        })
                        .show();
                horizontalProgressBar.setVisibility(View.GONE);
            }

        } else {
            requestPermissions();
        }
    }

    private boolean checkPermissions() {
        return ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }

    private void requestPermissions() {
        FragmentActivity activity = this.getActivity();
        if (activity != null) {
            ActivityCompat.requestPermissions(activity, new String[]{Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION}, LOCATION_PERMISSION_REQUEST_CODE);
        }
    }

    private void setNearByBusStopsWithInDistance(double userLatitude, double userLongitude, double dist) {
        if (dist < 5) {
            apiService.getNearByStops(dist, userLatitude, userLongitude)
                    .enqueue(new Callback<List<StopDetail>>() {
                        @Override
                        public void onResponse(Call<List<StopDetail>> call, Response<List<StopDetail>> response) {
                            if (response.body() != null) {
                                if (response.body().size() > 4) {
                                    busStopsHashMap = new HashMap<>();
                                    LatLngBounds.Builder builder = new LatLngBounds.Builder();
                                    builder.include(new LatLng(userLatitude, userLongitude));
                                    for (StopDetail data : response.body()) {
                                        LatLng latLng = new LatLng(data.getLatitude(), data.getLongitude());
                                        Marker marker = mMap.addMarker(new MarkerOptions().position(latLng).icon(BitmapDescriptorFactory.fromBitmap(new ViewMarker(context, data.getName(), Color.RED).getBitmap())));
                                        busStopsHashMap.put(marker, data);
                                        builder.include(latLng);
                                    }
                                    LatLngBounds bounds = builder.build();
                                    mMap.animateCamera(CameraUpdateFactory.newLatLngBounds(bounds, 0));
                                } else {
                                    setNearByBusStopsWithInDistance(userLatitude, userLongitude, (dist + 0.25));
                                }
                            }
                        }

                        @Override
                        public void onFailure(Call<List<StopDetail>> call, Throwable t) {
                            Log.e(TAG, "onFailure: " + t.getMessage());
                        }
                    });
        }
    }

    private void setUserLocation() {
        if (userLocation != null) {
            mMap.addMarker(new MarkerOptions().icon(BitmapDescriptorFactory.fromBitmap(new ViewMarker(context, "Your location ").getBitmap())).position(userLocation));
            mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(userLocation, 17));
        }
    }

    private boolean isLocationEnabled(LocationManager locationManager) {
        return locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) || locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER);
    }

    public void showRoutesBottomSheet(View view) {
        routesBottomSheetDialog.show();
    }

    private void showToast(String s) {
        showToast(s, "info");
    }

    private void showToast(String s, String about) {
        Toast.makeText(context, s, Toast.LENGTH_LONG).show();
        Log.e(TAG, about + "  : " + s);
    }

    @Override
    public void onResume() {
        super.onResume();
        if (getActivity() != null && getActivity().getWindow() != null) {
            getActivity().getWindow().addFlags(WINDOW_DECORATION_FLAG);
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        if (getActivity() != null && getActivity().getWindow() != null) {
            getActivity().getWindow().clearFlags(WINDOW_DECORATION_FLAG);
        }
    }

    public interface OnStopMarkerClickListener {
        void onStopMarkerClicked(StopDetail stop, Runnable fabClickCallback);
    }

}