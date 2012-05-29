package com.github.seqware.model;

import com.github.seqware.factory.Factory;

/**
 * Implements core functionality that is shared by classes that are
 * controlled by permissions and versionable (as well as Taggable)
 * 
 * It does not look like we have anything that supports only two ... so far
 * ACL only supports ACLable and Tag only supports Versionable though
 *
 * @author dyuen
 */
public abstract class Molecule<T extends Molecule> extends Atom<T> implements ACLable, Versionable<T> {
    
    public ACL getPermissions() {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    public void setPermissions(ACL acl) throws SecurityException {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    public long getVersion() {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    public void setVersion(String version) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    public T getPrecedingVersion() {
        return (T) Factory.getBackEnd().getPrecedingVersion(this);
    }

    public void setPrecedingVersion(T predecessor) {
        throw new UnsupportedOperationException("Not supported yet.");
    }    
}