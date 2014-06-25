/*
 * JBoss, Home of Professional Open Source.
 *
 * See the LEGAL.txt file distributed with this work for information regarding copyright ownership and licensing.
 *
 * See the AUTHORS.txt file distributed with this work for a full listing of individual contributors.
 */
package org.komodo.relational.model;

import java.util.Comparator;
import java.util.Iterator;
import java.util.Properties;
import java.util.Set;

import org.komodo.core.CoreArgCheck;
import org.komodo.core.CoreStringUtil;
import org.komodo.core.HashCodeUtil;
import org.komodo.core.IStatus;
import org.komodo.core.ModelType;
import org.komodo.core.Status;
import org.komodo.core.StringNameValidator;
import org.komodo.core.StringUtilities;
import org.komodo.relational.Messages;
import org.komodo.relational.Messages.RELATIONAL;
import org.komodo.relational.constants.RelationalConstants;
import org.komodo.relational.extension.RelationalModelExtensionConstants;


/**
 * 
 *
 * @since 8.0
 */
public abstract class RelationalObject implements RelationalConstants, RelationalModelExtensionConstants.PropertyKeysNoPrefix {
    @SuppressWarnings("javadoc")
	public static final String KEY_NAME = "NAME"; //$NON-NLS-1$
    @SuppressWarnings("javadoc")
    public static final String KEY_NAME_IN_SOURCE = "NAMEINSOURCE"; //$NON-NLS-1$
    @SuppressWarnings("javadoc")
    public static final String KEY_DESCRIPTION = "DESCRIPTION"; //$NON-NLS-1$
    
    @SuppressWarnings("javadoc")
    public static final int IGNORE = -1;
    @SuppressWarnings("javadoc")
    public static final int CREATE_ANYWAY = 0;
    @SuppressWarnings("javadoc")
    public static final int REPLACE = 1;
    @SuppressWarnings("javadoc")
    public static final int CREATE_UNIQUE_NAME = 2;
    
    private RelationalObject parent;
    private String  name;
    private String  nameInSource;
    private String  description;
    
    private int processType;
    
    protected IStatus currentStatus;
    
    private boolean isChecked = true;
    
    private int modelType = ModelType.PHYSICAL;
    
    private Properties extensionProperties = new Properties();
    
    private StringNameValidator nameValidator = new StringNameValidator();

    /**
     * RelationalReference constructor
     */
    public RelationalObject() {
        super();
        this.processType = CREATE_ANYWAY;
        this.currentStatus = Status.OK_STATUS; 
        this.isChecked = true;
    }
    
    /**
     * RelationalReference constructor
     * @param name the name of the object
     */
    public RelationalObject( String name ) {
        super();
        this.name = name;
        this.processType = CREATE_ANYWAY;
        this.isChecked = true;
    }
    


    /* (non-Javadoc)
	 * @see java.lang.Object#clone()
	 */
	@Override
	protected Object clone() throws CloneNotSupportedException {
		return super.clone();
	}
	
    /**
     * Set the object common properties
     * @param props the properties
     */
    public void setProperties(Properties props) {
        for( Object key : props.keySet() ) {
            String keyStr = (String)key;
            String value = props.getProperty(keyStr);

            if( value != null && value.length() == 0 ) {
                continue;
            }
            
            if( keyStr.equalsIgnoreCase(KEY_NAME) ) {
                setName(value);
            } else if(keyStr.equalsIgnoreCase(KEY_NAME_IN_SOURCE) ) {
                setNameInSource(value);
            } else if(keyStr.equalsIgnoreCase(KEY_DESCRIPTION) ) {
                setDescription(value);
            } 
        }
    	
        handleInfoChanged();
    }
	
	/**
	 * @param obj the relational reference
	 */
	public void inject(RelationalObject obj) {
		
	}
	/**
     * @return parent
     */
    public RelationalObject getParent() {
        return parent;
    }

    /**
     * @param parent Sets parent to the specified value.
     */
    public void setParent( RelationalObject parent ) {
        this.parent = parent;
        handleInfoChanged();
    }
    /**
     * @return name
     */
    public String getName() {
        return name;
    }
    /**
     * @param name Sets name to the specified value.
     */
    public void setName( String name ) {
    	if( StringUtilities.areDifferent(this.name, name) ) {
    		this.name = name;
    		handleInfoChanged();
    	}
    }
    /**
     * @return nameInSource
     */
    public String getNameInSource() {
        return nameInSource;
    }
    /**
     * @param nameInSource Sets nameInSource to the specified value.
     */
    public void setNameInSource( String nameInSource ) {
    	if( StringUtilities.areDifferent(this.nameInSource, nameInSource) ) {
    		this.nameInSource = nameInSource;
    		handleInfoChanged();
    	} 
    }
    /**
     * @return description
     */
    public String getDescription() {
        return description;
    }
    /**
     * @param description Sets description to the specified value.
     */
    public void setDescription( String description ) {
    	if( StringUtilities.areDifferent(this.description, description) ) {
    		this.description = description;
    		handleInfoChanged();
    	} 
    }
    
    /**
     * @return the model type
     */
    public int getModelType() {
        return this.modelType;
    }
    
    /**
     * @param value the model type
     */
    public void setModelType(int value) {
        this.modelType = value;
    }
    
    /**
     * @return type
     */
    public int getType() {
        return TYPES.UNDEFINED;
    }
    
    /**
     * @return the process type
     */
    public int getProcessType() {
        return this.processType;
    }

    /**
     * @param value the type of processing
     * 
     */
    public void setDoProcessType(int value) {
        this.processType = value;
    }
    
    /**
     * @return the isChecked state
     */
    public boolean isChecked() {
        return this.isChecked;
    }

    /**
     * sets selected flag
     * @param isChecked 'true' if the item is selected
     * 
     */
    public void setChecked(boolean isChecked) {
        this.isChecked = isChecked;
    }    

    /**
     * Set the extension properties
     * @param extProps the extension properties
     */
    public void setExtensionProperties(Properties extProps) {
    	clearExtensionProperties();
    	if(extProps!=null) {
    		Set<Object> propKeys = extProps.keySet();
    		for(Object propKey : propKeys) {
    			String strKey = (String)propKey;
    			String strValue = extProps.getProperty(strKey);
    			int index = strKey.indexOf(':');
    			if(index!=-1) {
    				strKey = strKey.substring(index+1);
    			}
    			// TODO: Supports ID Lookup is not being returned in DDL Options - need to resolve.
    			if(strKey!=null && !strKey.equalsIgnoreCase("Supports ID Lookup")) {  //$NON-NLS-1$
    				addExtensionProperty(strKey,strValue);
    			}
    		}
    	}
    }
    
    /**
     * Add an extension property
     * @param propName property name
     * @param propValue property value
     */
    public void addExtensionProperty(String propName, String propValue) {
    	if(propName!=null) this.extensionProperties.put(propName,propValue);
    }
    
    /**
     * remove an extension property
     * @param propName property name
     */
    public void removeExtensionProperty(String propName) {
    	this.extensionProperties.remove(propName);
    }
    
    /**
     * clear the extension properties
     */
    public void clearExtensionProperties() {
    	this.extensionProperties.clear();
    }

    /**
     * @return the extension properties
     */
    public Properties getExtensionProperties() {
    	return this.extensionProperties;
    }
    
    
    
    /**
     * @return the display name
     */
    public String getDisplayName() {
    	return TYPE_NAMES[getType()];
    }

    /**
     * @return the current status
     */
    public IStatus getStatus() {
    	return this.currentStatus;
    }

    /**
     * @return the string name validator
     */
    public StringNameValidator getNameValidator() {
    	return this.nameValidator;
    }

    /**
     * @param nameValidator the name validator
     * 
     */
    public void setNameValidator(StringNameValidator nameValidator) {
    	CoreArgCheck.isNotNull(nameValidator, "nameValidator"); //$NON-NLS-1$
    	this.nameValidator = nameValidator;
    }
    
    protected void handleInfoChanged() {
    	validate();
    }
    
    /**
     * Check name validity
     * @return 'true' if value, 'false' if not.
     */
    public final boolean nameIsValid() {
		if( this.getName() == null || this.getName().length() == 0 ) {
			return false;
		}
		// Validate non-null string
		String errorMessage = getNameValidator().checkValidName(this.getName());
		if( errorMessage != null && !errorMessage.isEmpty() ) {
			return false;
		}
		return true;
    }
    
    /**
     * @return the validation status
     */
    public IStatus validate() {
		if( this.getName() == null || this.getName().length() == 0 ) {
			this.currentStatus = new Status(IStatus.ERROR, PLUGIN_ID, 
					  Messages.getString(RELATIONAL.validate_error_nameCannotBeNullOrEmpty, getDisplayName()) );
			return this.currentStatus;
		}
		// Validate non-null string
		String errorMessage = getNameValidator().checkValidName(this.getName());
		if( errorMessage != null && !errorMessage.isEmpty() ) {
			this.currentStatus = new Status(IStatus.ERROR, PLUGIN_ID, errorMessage);
			return this.currentStatus;
		}
		this.currentStatus = Status.OK_STATUS;
		return this.currentStatus;
    }
    
	/* (non-Javadoc)
	 * @see java.lang.Object#toString()
	 */
	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder();
		sb.append(this.getClass().getName());
		sb.append(" : name = ").append(getName()); //$NON-NLS-1$
		return sb.toString();
	}
	
    /**
     * {@inheritDoc}
     * 
     * @see java.lang.Object#equals(java.lang.Object)
     */
    @Override
    public boolean equals( final Object object ) {
        if (this == object)
            return true;
        if (object == null)
            return false;
        if (getClass() != object.getClass())
            return false;
        final RelationalObject other = (RelationalObject)object;

        // string properties
        if (!CoreStringUtil.valuesAreEqual(getName(), other.getName())
                || !CoreStringUtil.valuesAreEqual(getNameInSource(), other.getNameInSource())
                || !CoreStringUtil.valuesAreEqual(getDescription(), other.getDescription())) {
            return false;
        }
        
        if( !(getType()==other.getType()) ) {
        	return false;
        }
        if( !(getModelType()==other.getModelType()) ) {
        	return false;
        }
        if( !(getProcessType()==other.getProcessType()) ) {
        	return false;
        }
        if(!getExtensionProperties().equals(other.getExtensionProperties())) {
        	return false;
        }

        return true;
    }
    
    /**
     * {@inheritDoc}
     * 
     * @see java.lang.Object#hashCode()
     */
    @Override
    public int hashCode() {
        int result = HashCodeUtil.hashCode(0, getType());

        result = HashCodeUtil.hashCode(result, getType());
        result = HashCodeUtil.hashCode(result, getModelType());
        result = HashCodeUtil.hashCode(result, getProcessType());
        
        // string properties
        if (!CoreStringUtil.isEmpty(getName())) {
            result = HashCodeUtil.hashCode(result, getName());
        }
        
        if (!CoreStringUtil.isEmpty(getNameInSource())) {
            result = HashCodeUtil.hashCode(result, getNameInSource());
        }

        if (getDescription() != null && !getDescription().isEmpty()) {
            result = HashCodeUtil.hashCode(result, getDescription());
        }

        if ((this.extensionProperties != null) && !this.extensionProperties.isEmpty()) {
        	Iterator<Object> keyIter = this.extensionProperties.keySet().iterator();
        	while(keyIter.hasNext()) {
        		String key = (String)keyIter.next();
        		String value = this.extensionProperties.getProperty(key);
        		result = HashCodeUtil.hashCode(result, key);
        		result = HashCodeUtil.hashCode(result, value);
        	}
        }

        return result;
    } 
    
    /**
     * Reference comparator
     */
    public class ReferenceComparator implements Comparator<RelationalObject> {
    	@Override
    	public int compare(RelationalObject x, RelationalObject y) {
    		RelationalObject xParent = x.getParent();
    		RelationalObject yParent = y.getParent();

    		// if either of parents null, just use names
    		if(xParent==null || yParent==null) {
        	    return x.getName().compareTo(y.getName());
    		}
    		
    		int parentResult = xParent.getName().compareTo(yParent.getName());
    	    if (parentResult != 0) return parentResult;

    	    // if parent names match, use reference name
    	    return x.getName().compareTo(y.getName());
    	}

    }       
}
