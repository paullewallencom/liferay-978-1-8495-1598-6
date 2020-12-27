/**
* poll - jQuery plugin for dynamic polling.
*
* Project home page:
* http://launchpad.net/jquery.poll
*
* Inspired by:
* http://enfranchisedmind.com/blog/posts/jquery-periodicalupdater-ajax-polling/
*
* Copyright (c) 2009 by Ethan Fremen <mindlace@canhas.biz>
*
* Licensed under the MIT license:
* http://www.opensource.org/licenses/mit-license.php
*
* Version: 0.3
*/
/*
Usage: 
 All poll options are optional.
var pollopts = {
  //A random name will be assigned if you don't assign one
  name: 'name-of-poll', 
  //minimum wait between calls in msec, defaults to 1000
  min_wait: 6000, 
  //maximum wait between calls in msec, defaults to 30000
  max_wait: 12000, 
  //amount to multiply the wait by if the data is unchanged. defaults to 2
  wait_multiplier: 4, 
  // log poll events to console.log , default true
  doLog: false, 
  // A function that decides how to adjust the wait period after each call.
  // Should return an integer
  adjustWait: function(xhr, textStatus, current_wait) { ... }, .
};
// simplest way to start polling. 
// ajaxopts are defined as per http://docs.jquery.com/Ajax/jQuery.ajax#toptions 
var mypoll = $.poll(ajaxopts); 
// start polling with custom poll options
var mypoll = $.poll(ajaxopts,pollopts);
// get a poll object, but don't start polling
var mypoll = $.poll(ajaxopts,pollopts,true);
// can also do $.poll(ajaxopts,null,true);
// stop the poll
mypoll.stop = true;
// make a one-time change to the poll wait interval
mypoll.setWait(500);
// change the ajax options for the live poll
mypoll.setAjaxOptions(ajaxopts);
*/
(function($) {
    var randomString = function(numchars) {
        var chars = "0123456789ABCDEFGHJKMNPQRSTUVWXYZabcdefghkmnpqrstuvwxyz";
        var randomstring = '';
        for (var i=0; i<numchars; i++) {
            var rnum = Math.floor(Math.random() * chars.length);
            randomstring += chars.substring(rnum,rnum+1);
        }
        return randomstring;
    };
    
    var Poller = function(ajaxopt, pollopt) {
        this.setAjaxOptions(ajaxopt);
        this.name = (typeof(pollopt.name) != 'undefined') ? pollopt.name : randomString(4);
        this.min_wait = (typeof(pollopt.min_wait) != 'undefined') ? pollopt.min_wait : 1000;
        this.max_wait = (typeof(pollopt.max_wait) != 'undefined') ? pollopt.max_wait : 30000;
        this.current_wait = this.min_wait;
        this.wait_multiplier = (typeof(pollopt.wait_multiplier) != 'undefined') ? pollopt.wait_multiplier: 2;
        this.stop = false;
        this.doLog = (typeof(pollopt.doLog) != 'undefined') ? pollopt.doLog : true;
        this.current_poll = false;
        this.userdata = {};
        if (typeof(pollopt.adjustWait) != 'undefined') {
            this.adjustWait = pollopt.adjustWait;
        }
    };
    
    Poller.prototype.log = function(logstring) {
        if (this.doLog && typeof(console) != 'undefined' && console != null) {
            console.log('Poll '+ this.name+': '+logstring);
        }
    };
    
    /**
     * Set the ajax options, copying the default ajaxSettings so we can override
     * the 'complete' callback as necesary, and to allow data to be a callback.
     * You can adjust settings of a "live" poll with this function.
     */
     Poller.prototype.setAjaxOptions = function(options) {
         if (typeof(options) == 'string') {
             options = { url: options };
         }
         if (this.settings) {
             oldsettings = this.settings;
         } else {
             oldsettings = {};
         }
         var newSettings = jQuery.extend(true, {}, jQuery.ajaxSettings, oldsettings);
         if (options) {
             jQuery.extend(true, newSettings, options);
         }
         if (typeof(newSettings.data) != 'undefined') {
             if (jQuery.isFunction(newSettings.data)) {
                 this.dataCallback = newSettings.data;
             } else {
                 this.userdata = newSettings.data;
             }
             delete newSettings.data;
         }
         if (jQuery.isFunction(newSettings.complete)) {
             this.userComplete = newSettings.complete;
             delete newSettings.complete;
         }
         this.settings = newSettings;
     };
     /**
      * Check to see if we should really fetch now. 
      * This is where we setTimeout for repeated calling, so that
      * the wait period may be manipulated by other javascript
      * using the setWait() function.
      */
     Poller.prototype.fetch = function() {
         self = this;
         if (!this.stop) {
             if (!this.current_poll) {
                 this.fetchNow();
             } else {
                 this.log('last XHR not complete, skipping fetch');
             }
             var anotherFetch = function() {
                 self.fetch();
             }
             this.log('next check in '+this.current_wait+'ms');
             this.current_poll = setTimeout(anotherFetch, this.current_wait);
         } else {
             this.log('stopped.');
         }
     };
     
     /**
      * If the "data" attribute was provided as a non-function, we wrap it as one.
      */
     Poller.prototype.dataCallback = function() {
         return this.userdata;
     };
     
     /**
      * Actually perform the ajax request.
      */
     Poller.prototype.fetchNow = function() {
         var calls = {
             ifModified: true,
             complete: this.completeCallback,
             data: this.dataCallback(),
         };
         var opts = $.extend(true, calls, this.settings);
         $.ajax(opts);
     };
     
     /**
      * Provide a custom complete callback so we can continue to poll.
      * nb. using self since this is a callback - self is set to this
      * in fetch().
      */
     Poller.prototype.completeCallback = function(xhr, textStatus) {
         self.log('complete: '+textStatus);
         self.current_poll = false;
         if ($.isFunction(self.adjustWait)) {
             self.current_wait = self.adjustWait(xhr, textStatus, self.current_wait);
         } else if (textStatus != 'success') {
             // increase the wait for server error, timeout, notmodified, or parser error.
             next_wait = self.current_wait * self.wait_multiplier;
             if (next_wait <= self.max_wait) {
                 self.current_wait = next_wait;
             } else {
                 self.current_wait = self.max_wait;
             }
             if ($.isFunction(self.userError)) {
                 self.userError(xhr, textStatus);
             }
         }
         if ($.isFunction(self.userComplete)) {
             self.userComplete(xhr, textStatus);
         }
     };
     
     /**
      * This function allows you to change the polling period on a one-time basis.
      */
     Poller.prototype.setWait = function(wait) {
         // if the time is set to less than the current wait
         // and we have an active timer, clear it.
         if (wait < this.current_wait && this.current_poll) { 
             clearTimeout(this.current_poll);
             this.current_poll = false;
         }
         if (wait) { this.current_wait = wait; }
         this.fetch();
     };
     
    $.poll = function(ajaxopt, pollopt, dontstart) {
        if (typeof(pollopt) == 'undefined' || pollopt == null) { pollopt = {}; }
        poller = new Poller(ajaxopt, pollopt);
        if (!dontstart) {
            poller.fetch();
        }
        return poller;
    };
 
 })(jQuery);