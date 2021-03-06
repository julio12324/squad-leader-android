/*******************************************************************************
 * Copyright 2013-2014 Esri
 * 
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 * 
 *  http://www.apache.org/licenses/LICENSE-2.0
 *  
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 ******************************************************************************/
package com.esri.squadleader.controller;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;

import android.content.res.AssetManager;
import android.graphics.drawable.Drawable;
import android.os.Environment;
import android.util.Log;

import com.esri.android.map.GraphicsLayer;
import com.esri.core.geometry.Geometry;
import com.esri.core.geometry.GeometryEngine;
import com.esri.core.geometry.Point;
import com.esri.core.geometry.SpatialReference;
import com.esri.core.map.Graphic;
import com.esri.core.symbol.PictureMarkerSymbol;
import com.esri.core.symbol.Symbol;
import com.esri.core.symbol.advanced.Message;
import com.esri.core.symbol.advanced.MessageGroupLayer;
import com.esri.core.symbol.advanced.MessageHelper;
import com.esri.core.symbol.advanced.SymbolDictionary;
import com.esri.militaryapps.model.Geomessage;
import com.esri.squadleader.util.Utilities;

/**
 * A controller for ArcGIS Runtime advanced symbology. Use this class when you want to use
 * MessageGroupLayer, MessageProcessor, SymbolDictionary, and MIL-STD-2525C symbols.
 */
public class AdvancedSymbolController extends com.esri.militaryapps.controller.AdvancedSymbolController {
    
    private static final String TAG = AdvancedSymbolController.class.getSimpleName();
    private static final SpatialReference WGS1984 = SpatialReference.create(4326);

    private final MapController mapController;
    private final MessageGroupLayer groupLayer;
    private final GraphicsLayer spotReportLayer;
    private final Symbol spotReportSymbol;

    /**
     * Creates a new AdvancedSymbolController.
     * @param mapController the application's MapController.
     * @param assetManager the application's AssetManager, from which the advanced symbology database
     *                     will be copied.
     * @param symbolDictionaryDirname the name of the asset directory that contains the advanced symbology
     *                                database.
     * @param spotReportIcon the Drawable for putting spot reports on the map.
     * @throws FileNotFoundException if the advanced symbology database is absent or corrupt.
     */
    public AdvancedSymbolController(
            MapController mapController,
            AssetManager assetManager,
            String symbolDictionaryDirname,
            Drawable spotReportIcon) throws FileNotFoundException {
        super(mapController);
        this.mapController = mapController;
        File downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
        File symDictDir = new File(downloadsDir, symbolDictionaryDirname);
        if (!symDictDir.exists()) {
            try {
                Utilities.copyAssetToDir(assetManager, symbolDictionaryDirname, downloadsDir.getAbsolutePath());
            } catch (IOException e) {
                Log.e(TAG, "Could not copy symbol dictionary directory to " + symDictDir.getAbsolutePath(), e);
            }
        }
        
        spotReportLayer = new GraphicsLayer();
        mapController.addLayer(spotReportLayer);
        
        groupLayer = new MessageGroupLayer(SymbolDictionary.DictionaryType.MIL2525C, symDictDir.getAbsolutePath());
        mapController.addLayer(groupLayer);
        
        spotReportSymbol = new PictureMarkerSymbol(spotReportIcon);
    }
    
    @Override
    protected Integer displaySpotReport(double x, double y, final int wkid, Integer graphicId, Geomessage geomessage) {
        try {
            Geometry pt = new Point(x, y);
            if (null != mapController.getSpatialReference() && wkid != mapController.getSpatialReference().getID()) {
                pt = GeometryEngine.project(pt, SpatialReference.create(wkid), mapController.getSpatialReference());
            }
            if (null != graphicId) {
                spotReportLayer.updateGraphic(graphicId, pt);
                spotReportLayer.updateGraphic(graphicId, geomessage.getProperties());
            } else {
                Graphic graphic = new Graphic(pt, spotReportSymbol, geomessage.getProperties());
                graphicId = spotReportLayer.addGraphic(graphic);
                
            }
            return graphicId;
        } catch (NumberFormatException nfe) {
            Log.e(TAG, "Could not parse spot report", nfe);
            return null;
        }
    }
    
    @Override
    protected String translateMessageTypeName(String geomessageTypeName) {
        if ("trackrep".equals(geomessageTypeName)) {
            geomessageTypeName = "position_report";
        }
        return geomessageTypeName;
    }
    
    @Override
    protected String translateColorString(String geomessageColorString) {
        if ("1".equals(geomessageColorString)) {
            geomessageColorString = "red";
        } else if ("2".equals(geomessageColorString)) {
            geomessageColorString = "green";
        } else if ("3".equals(geomessageColorString)) {
            geomessageColorString = "blue";
        } else if ("4".equals(geomessageColorString)) {
            geomessageColorString = "yellow";
        }
        return geomessageColorString;
    }

    @Override
    protected boolean processMessage(Geomessage geomessage) {
        String action = (String) geomessage.getProperty(Geomessage.ACTION_FIELD_NAME);
        Message message;
        if (MessageHelper.MESSAGE_ACTION_VALUE_HIGHLIGHT.equalsIgnoreCase(action)) {
            message = MessageHelper.create2525CHighlightMessage(
                    geomessage.getId(),
                    (String) geomessage.getProperty(Geomessage.TYPE_FIELD_NAME),
                    true);
        } else if (MessageHelper.MESSAGE_ACTION_VALUE_UNHIGHLIGHT.equalsIgnoreCase(action)) {
            message = MessageHelper.create2525CHighlightMessage(
                    geomessage.getId(),
                    (String) geomessage.getProperty(Geomessage.TYPE_FIELD_NAME),
                    false);
        } else if (MessageHelper.MESSAGE_ACTION_VALUE_REMOVE.equalsIgnoreCase(action)) {
            message = MessageHelper.create2525CRemoveMessage(
                    geomessage.getId(),
                    (String) geomessage.getProperty(Geomessage.TYPE_FIELD_NAME));
        } else {
            message = MessageHelper.create2525CUpdateMessage(
                    geomessage.getId(),
                    (String) geomessage.getProperty(Geomessage.TYPE_FIELD_NAME),
                    true);
            message.setProperties(geomessage.getProperties());
            message.setID(geomessage.getId());
        }
        
        return groupLayer.getMessageProcessor().processMessage(message);
    }
    
    @Override
    protected boolean processHighlightMessage(String geomessageId, String messageType, boolean highlight) {
        Message message = MessageHelper.create2525CHighlightMessage(geomessageId, messageType, highlight);
        return groupLayer.getMessageProcessor().processMessage(message);
    }

    @Override
    public String[] getMessageTypesSupported() {
        return groupLayer.getMessageProcessor().getMessageTypesSupported();
    }

    @Override
    public String getActionPropertyName() {
        return MessageHelper.MESSAGE_ACTION_PROPERTY_NAME;
    }

    @Override
    protected void processRemoveGeomessage(String geomessageId, String messageType) {
        Message message = MessageHelper.create2525CRemoveMessage(geomessageId, messageType);
        groupLayer.getMessageProcessor().processMessage(message);
    }
    
}
