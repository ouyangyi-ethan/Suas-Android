package com.example.suas.weather.network

import com.example.suas.weather.suas.*
import zendesk.suas.Action
import zendesk.suas.AsyncMiddleware
import retrofit2.Call
import retrofit2.Response

class WeatherService(val autocomplete: AutocompleteService, val weather: WundergroundService) {

    fun findCities(query: String): Action<*> {
        return AsyncMiddleware.create { dispatcher, _ ->
            dispatcher.dispatch(LoadSuggestedCities(query))
            loadCitiesFromNetwork(query){
                when(it) {
                    is NetworkError -> dispatcher.dispatch(SuggestedCitiesError())
                    is SuggestionResult -> dispatcher.dispatch(SuggestedCitiesLoaded(it.result))
                }
            }
        }
    }

    fun loadWeather(location: StateModels.Location): Action<*> {
        return AsyncMiddleware.create { dispatcher, getState ->
            dispatcher.dispatch(LoadWeather(location))

            val observations = getState.state.getState(StateModels.LoadedObservations::class.java)
            val o = observations?.data?.get(location)

            if(o != null) {
                dispatcher.dispatch(LoadWeatherSuccess(o, location))

            } else {
                loadWeatherFromNetwork(location) {
                    when (it) {
                        is NetworkError -> dispatcher.dispatch(LoadWeatherError())
                        is ConditionResult -> dispatcher.dispatch(LoadWeatherSuccess(it.result, location))
                    }
                }
            }
        }
    }

    private fun loadCitiesFromNetwork(query: String, callback: Callback) {
        autocomplete
                .autocomplete(query = query)
                .bridge(callback){ (items) -> SuggestionResult(items) }
    }

    private fun loadWeatherFromNetwork(location: StateModels.Location, callback : Callback) {
        weather
                .getConditions(id = location.id)
                .bridge(callback) { (currentObservation) -> ConditionResult(currentObservation) }
    }

    private fun <E, F> Call<E>.bridge(callback: Callback, convert: (data: E) -> Success<F>) {
        enqueue(object : retrofit2.Callback<E>{

            override fun onResponse(call: Call<E>?, response: Response<E>?) {
                val result = if(response?.isSuccessful ?: false) {
                    response?.body()?.let(convert) ?: NetworkError("Err0r")
                } else {
                    NetworkError(response?.message() ?: "Err0r")
                }

                callback(result)
            }

            override fun onFailure(call: Call<E>?, t: Throwable?) {
                callback(NetworkError(t?.message ?: "Err0r"))
            }

        })
    }

}