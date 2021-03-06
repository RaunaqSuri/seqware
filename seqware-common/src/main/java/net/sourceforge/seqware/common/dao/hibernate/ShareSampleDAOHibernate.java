package net.sourceforge.seqware.common.dao.hibernate;

import java.lang.reflect.InvocationTargetException;
import java.util.List;

import net.sourceforge.seqware.common.dao.ShareSampleDAO;
import net.sourceforge.seqware.common.model.ShareSample;
import net.sourceforge.seqware.common.util.NullBeanUtils;

import org.apache.commons.beanutils.BeanUtilsBean;
import org.springframework.orm.hibernate3.support.HibernateDaoSupport;

/**
 * <p>ShareSampleDAOHibernate class.</p>
 *
 * @author boconnor
 * @version $Id: $Id
 */
public class ShareSampleDAOHibernate extends HibernateDaoSupport implements ShareSampleDAO {
  /** {@inheritDoc} */
  @Override
  public void insert(ShareSample shareSample) {
    this.getHibernateTemplate().save(shareSample);
  }

  /** {@inheritDoc} */
  @Override
  public void update(ShareSample shareSample) {
    this.getHibernateTemplate().update(shareSample);
  }

  /** {@inheritDoc} */
  @Override
  public void delete(ShareSample shareSample) {
    this.getHibernateTemplate().delete(shareSample);
  }

  /** {@inheritDoc} */
  @Override
  @SuppressWarnings("rawtypes")
  public ShareSample findByID(Integer shareSampleID) {
    String query = "from ShareSample as s where s.shareSampleId = ?";
    ShareSample obj = null;
    Object[] parameters = { shareSampleID };
    List list = this.getHibernateTemplate().find(query, parameters);
    if (list.size() > 0) {
      obj = (ShareSample) list.get(0);
    }
    return obj;
  }

  /** {@inheritDoc} */
  @Override
  @SuppressWarnings("unchecked")
  public List<ShareSample> findByOwnerID(Integer registrationID) {
    String query = "from ShareSample as s where s.registration.registrationId = ?";
    Object[] parameters = { registrationID };
    return this.getHibernateTemplate().find(query, parameters);
  }

  /** {@inheritDoc} */
  @Override
  @SuppressWarnings({ "unchecked" })
  public ShareSample findBySWAccession(Integer swAccession) {
    String query = "from ShareSample as s where s.swAccession = ?";
    ShareSample shareSample = null;
    Object[] parameters = { swAccession };
    List<ShareSample> list = this.getHibernateTemplate().find(query, parameters);
    if (list.size() > 0) {
      shareSample = (ShareSample) list.get(0);
    }
    return shareSample;
  }

  /** {@inheritDoc} */
  @Override
  public ShareSample updateDetached(ShareSample shareSample) {
    ShareSample dbObject = findByID(shareSample.getShareSampleId());
    try {
      BeanUtilsBean beanUtils = new NullBeanUtils();
      beanUtils.copyProperties(dbObject, shareSample);
      return (ShareSample) this.getHibernateTemplate().merge(dbObject);
    } catch (IllegalAccessException e) {
      e.printStackTrace();
    } catch (InvocationTargetException e) {
      e.printStackTrace();
    }
    return null;
  }

    /** {@inheritDoc} */
    @Override
    public List<ShareSample> list() {
        throw new UnsupportedOperationException("Not supported yet.");
    }
}
