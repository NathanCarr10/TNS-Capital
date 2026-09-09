"""
Dashboard: Streamlit-based analytics dashboard for TNS Capital trading data.

Run with: streamlit run analytics/dashboard.py
"""

import streamlit as st
import pandas as pd
import logging
from etl.extract import extract_all_data
from processing.clean import clean_all_data, validate_data_quality
from analysis.insights import generate_all_insights
from visualization.charts import generate_all_charts

# Setup logging
logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)

# Streamlit config
st.set_page_config(page_title="TNS Capital Analytics", layout="wide")


def main():
    """Main dashboard application."""
    st.title("TNS Capital Trading Analytics Dashboard")
    st.markdown("---")
    
    # Sidebar
    st.sidebar.title("Controls")
    if st.sidebar.button("🔄 Refresh Data"):
        st.cache_data.clear()
        st.success("Cache cleared! Data will refresh on next load.")
    
    # Load and process data
    st.sidebar.info("Loading trading data...")
    
    try:
        # Extract
        data = extract_all_data()
        
        # Validate
        validation = validate_data_quality(data)
        
        if not validation['passed']:
            st.warning("⚠️ Data quality issues detected:")
            for issue in validation['issues']:
                st.write(f"• {issue}")
        
        # Clean
        cleaned_data = clean_all_data(data)
        
        # Analyze
        insights = generate_all_insights(cleaned_data)
        
        # Visualize
        charts = generate_all_charts(insights)
        
        # Display insights
        st.header("📊 Key Metrics")
        
        col1, col2, col3, col4 = st.columns(4)
        
        with col1:
            total_value = insights['trade_volume'].get('total_trade_value', 0)
            st.metric("Total Trade Value", f"${total_value:,.2f}")
        
        with col2:
            total_orders = insights['trade_volume'].get('total_orders', 0)
            st.metric("Total Orders", total_orders)
        
        with col3:
            fill_rate = insights['fill_rate'].get('fill_rate_percent', 0)
            st.metric("Fill Rate", f"{fill_rate}%")
        
        with col4:
            avg_order = (
                insights['trade_volume'].get('total_trade_value', 0) / 
                max(insights['trade_volume'].get('total_orders', 1), 1)
            )
            st.metric("Avg Order Value", f"${avg_order:,.2f}")
        
        st.markdown("---")
        
        # Visualizations
        st.header("📈 Visualizations")
        
        tab1, tab2, tab3, tab4 = st.tabs(
            ["Trade Volume", "Fill Rate", "Instrument Exposure", "Account Activity"]
        )
        
        with tab1:
            if charts['trade_volume']:
                st.pyplot(charts['trade_volume'])
        
        with tab2:
            if charts['fill_rate']:
                st.pyplot(charts['fill_rate'])
        
        with tab3:
            if charts['instrument_exposure']:
                st.pyplot(charts['instrument_exposure'])
        
        with tab4:
            if charts['account_activity']:
                st.pyplot(charts['account_activity'])
        
        st.markdown("---")
        
        # Data tables
        st.header("📋 Detailed Data")
        
        table_tab1, table_tab2, table_tab3 = st.tabs(
            ["Account Activity", "Instrument Exposure", "Orders Sample"]
        )
        
        with table_tab1:
            st.subheader("Account Activity")
            st.dataframe(insights['account_activity'], use_container_width=True)
        
        with table_tab2:
            st.subheader("Instrument Exposure")
            st.dataframe(insights['instrument_exposure'], use_container_width=True)
        
        with table_tab3:
            st.subheader("Recent Orders (Sample)")
            st.dataframe(cleaned_data['orders'].head(20), use_container_width=True)
        
        logger.info("Dashboard rendered successfully")
        
    except Exception as e:
        st.error(f"❌ Error loading dashboard: {str(e)}")
        logger.error(f"Dashboard error: {e}", exc_info=True)


if __name__ == "__main__":
    main()
