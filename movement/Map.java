package movement;

import java.util.Vector;

import utilities.ByteArray;

public class Map {
	private static final int MAP_CELLS_COUNT = 560;
    public Class<Map> mapClass;
    public int mapVersion;
    public boolean encrypted;
    public int encryptionVersion;
    public int groundCRC;
    public double zoomScale = 1;
    public int zoomOffsetX;
    public int zoomOffsetY;
    public int groundCacheCurrentlyUsed = 0;
    public int id;
    public int relativeId;
    public int mapType;
    public int backgroundsCount;
    public Vector<Fixture> backgroundFixtures;
    public int foregroundsCount;
    public Vector<Fixture> foregroundFixtures;
    public int subareaId;
    public int shadowBonusOnEntities;
    public int backgroundColor;
    public int backgroundRed;
    public int backgroundGreen;
    public int backgroundBlue;
    public int topNeighbourId;
    public int bottomNeighbourId;
    public int leftNeighbourId;
    public int rightNeighbourId;
    public boolean useLowPassFilter;
    public boolean useReverb;
    public int presetId;
    public int cellsCount;
    public int layersCount;
    public boolean isUsingNewMovementSystem = false;
    public Vector<Layer> layers;
    public Vector<CellData> cells;
    public Vector<Integer> topArrowCell;
    public Vector<Integer> leftArrowCell;
    public Vector<Integer> bottomArrowCell;
    public Vector<Integer> rightArrowCell;
    private boolean _parsed;
    private boolean _failed;
    //private Vector<> _gfxList;
    //private Vector<> _gfxCount;
    
    public Map() {
    	this.mapClass = Map.class;
	}
    
    public boolean getParsed() {
    	return this._parsed;
    }
    
    public boolean getFailed() {
    	return this._failed;
    }
    
    public void fromRaw(ByteArray raw, String decryptionKey) {
    	raw.readByte(); // 77
    	this.mapVersion = raw.readByte();
    	this.id = raw.readInt();
    	if(this.mapVersion >= 7) {
    		this.encrypted = raw.readBoolean();
    		this.encryptionVersion = raw.readByte();
    		int dataLen = raw.readInt();
    		if(this.encrypted) {
    			if (decryptionKey == null)
                    throw new Error("Map decryption key is empty.");
    			byte[] encryptedData;
    			encryptedData = raw.readBytes(dataLen);
    			for(int i = 0; i < dataLen; ++i)
    				encryptedData[i] = (byte) (encryptedData[i] ^ decryptionKey.charAt(i % decryptionKey.length()));
    			raw.setArray(encryptedData);
    		}
    	}
		this.relativeId = raw.readInt();
		this.mapType = raw.readByte();
		this.subareaId = raw.readInt();
		this.topNeighbourId = raw.readInt();
		this.bottomNeighbourId = raw.readInt();
		this.leftNeighbourId = raw.readInt();
		this.rightNeighbourId = raw.readInt();
		this.shadowBonusOnEntities = raw.readInt();
		if(this.mapVersion >= 3) {
            this.backgroundRed = raw.readByte();
            this.backgroundGreen = raw.readByte();
            this.backgroundBlue = raw.readByte();
            this.backgroundColor = ((((this.backgroundRed & 0xFF) << 16) | ((this.backgroundGreen & 0xFF) << 8)) | (this.backgroundBlue & 0xFF));
		}
		if(this.mapVersion >= 4) {
            this.zoomScale = (raw.readShort() / 100);
            this.zoomOffsetX = raw.readShort();
            this.zoomOffsetY = raw.readShort();
            if(this.zoomScale < 1) {
            	this.zoomScale = 1;
            	this.zoomOffsetX = (this.zoomOffsetY = 0);
            }
            this.useLowPassFilter = (raw.readByte() == 1);
            this.useReverb = (raw.readByte() == 1);
            if(this.useReverb)
            	this.presetId = raw.readInt();
            else
            	this.presetId = -1;
            Fixture bg;
            this.backgroundsCount = raw.readByte();
            this.backgroundFixtures = new Vector<Fixture>();
            for(int i = 0; i < this.backgroundsCount; ++i) {
            	bg = new Fixture(this);
            	bg.fromRaw(raw);
            	this.backgroundFixtures.add(bg);
            }
            this.foregroundsCount = raw.readByte();
            this.foregroundFixtures = new Vector<Fixture>();
            for(int i = 0; i < this.foregroundsCount; ++i) {
            	bg = new Fixture(this);
            	bg.fromRaw(raw);
            	this.foregroundFixtures.add(bg);
            }
            this.cellsCount = MAP_CELLS_COUNT;
            raw.readInt();
            this.groundCRC = raw.readInt();
            this.layersCount = raw.readByte();
            this.layers = new Vector<Layer>();
            Layer layer;
            for(int i = 0; i < layersCount; ++i) {
            	layer = new Layer(this);
            	layer.fromRaw(raw, this.mapVersion);
            	this.layers.add(layer);
            }
            this.cells = new Vector<CellData>();
            CellData cd;
            int _oldMvtSystem = -1;
            for(int i = 0; i < cellsCount; ++i) {
            	cd = new CellData(this, i);
            	cd.fromRaw(raw);
            	if(_oldMvtSystem == -1)
            		_oldMvtSystem = cd.moveZone;
            	if(cd.moveZone != _oldMvtSystem)
            		this.isUsingNewMovementSystem = true;
            	this.cells.add(cd);
            }
            this._parsed = true;
		}
    }
}
